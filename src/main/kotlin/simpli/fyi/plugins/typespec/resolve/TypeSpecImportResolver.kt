package simpli.fyi.plugins.typespec.resolve

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import simpli.fyi.plugins.typespec.psi.TypeSpecFile
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/**
 * Turns any `import` specifier into a [TypeSpecFile], both forms the owner reported
 * ([ADR 0010](../../../../../../../../docs/adr/0010-library-import-resolution.md),
 * [plan 05](../../../../../../../../docs/plans/05-import-and-decorator-navigation.md) M5.6a):
 * relative (`./x.tsp`, `../x`) and bare (`@scope/pkg`, `pkg`, `@scope/pkg/sub`).
 *
 * This is a **lookup**, never a **search** (ADR 0010 D1): one specifier resolves to at most one
 * file via targeted `VirtualFile.findChild`/`findFileByRelativePath` calls, entering
 * `node_modules` only at a path the user literally wrote in an `import`. Nothing here touches
 * [TypeSpecSearchScopes] or any [com.intellij.psi.search.GlobalSearchScope] — `node_modules`
 * stays excluded from tier C exactly as ADR 0008 shipped it.
 */
object TypeSpecImportResolver {

    /**
     * Resolves [specifier] (the import string's contents, no surrounding quotes) as written in
     * [from]. Returns `null` — silently, never an exception — when the target does not exist,
     * `package.json` is malformed, or the resolved target is not a `.tsp` file (every real
     * library entry point also `import`s a `.js` decorator implementation; that is not a
     * navigation target — ADR 0010 D2).
     *
     * Cached per importing file (not project-wide — ADR 0010 §M5.6a "Approach"), invalidated on
     * any PSI change, matching [TypeSpecImportGraph.transitiveClosure]'s cache shape.
     */
    fun resolve(from: TypeSpecFile, specifier: String): TypeSpecFile? {
        val cache: ConcurrentHashMap<String, Optional<TypeSpecFile>> =
            CachedValuesManager.getCachedValue(from) {
                CachedValueProvider.Result.create(
                    ConcurrentHashMap<String, Optional<TypeSpecFile>>(),
                    PsiModificationTracker.MODIFICATION_COUNT,
                )
            }
        return cache.computeIfAbsent(specifier) { Optional.ofNullable(computeResolve(from, specifier)) }
            .orElse(null)
    }

    private fun computeResolve(from: TypeSpecFile, specifier: String): TypeSpecFile? {
        val baseDir = from.virtualFile?.parent ?: return null
        val target = if (specifier.startsWith("./") || specifier.startsWith("../")) {
            resolveRelative(baseDir, specifier)
        } else {
            resolveBare(baseDir, specifier)
        } ?: return null

        // ADR 0010 D4 — canonicalise before handing to PsiManager, collapsing a symlinked
        // workspace package (npm workspaces monorepo convention) back to its real project path.
        // A broken symlink yields null here, which this treats as unresolved.
        val canonical = target.canonicalFile ?: return null
        if (canonical.isDirectory || canonical.extension != "tsp") return null

        return PsiManager.getInstance(from.project).findFile(canonical) as? TypeSpecFile
    }

    private fun resolveRelative(baseDir: VirtualFile, specifier: String): VirtualFile? {
        val target = baseDir.findFileByRelativePath(specifier) ?: return null
        return if (target.isDirectory) entryPointOf(target) else target
    }

    /**
     * Walks **up** from [baseDir], checking `<dir>/node_modules/<packageName>` at each level
     * (ADR 0010 D2) — mandatory for npm-workspaces monorepos, where the only `node_modules` may
     * be several levels above the importing file. Stops at the VFS root.
     */
    private fun resolveBare(baseDir: VirtualFile, specifier: String): VirtualFile? {
        val (packageName, subPath) = splitBareSpecifier(specifier)
        var dir: VirtualFile? = baseDir
        while (dir != null) {
            ProgressManager.checkCanceled()
            val nodeModules = dir.findChild("node_modules")
            val packageDir = nodeModules?.findFileByRelativePath(packageName)?.takeIf { it.isDirectory }
            if (packageDir != null) {
                return if (subPath == null) {
                    entryPointOf(packageDir)
                } else {
                    // ADR 0010 D2.3 — deliberate simplification: a trailing subpath is resolved
                    // relative to the package directory; real `exports` subpath maps are not
                    // implemented.
                    val sub = packageDir.findFileByRelativePath(subPath) ?: return null
                    if (sub.isDirectory) entryPointOf(sub) else sub
                }
            }
            dir = dir.parent
        }
        return null
    }

    /** Splits `@scope/pkg/sub/path` into (`@scope/pkg`, `sub/path`) and `pkg/sub` into (`pkg`, `sub`). */
    private fun splitBareSpecifier(specifier: String): Pair<String, String?> {
        val segments = specifier.split('/')
        val nameSegmentCount = if (specifier.startsWith("@")) 2 else 1
        if (segments.size <= nameSegmentCount) return specifier to null
        val packageName = segments.take(nameSegmentCount).joinToString("/")
        val subPath = segments.drop(nameSegmentCount).joinToString("/")
        return packageName to subPath
    }

    /**
     * Upstream's entry-point order for a directory, shared by every directory-shaped target
     * (bare package directory, relative-import-to-directory, bare-subpath-to-directory — ADR
     * 0010 D2): `exports["."]` under the `"typespec"` condition → `tspMain` → `main` →
     * `main.tsp`. Verified against `entrypoint-resolution.js`/`source-loader.js`, not guessed —
     * a naive `lib/main.tsp` is wrong for `@typespec/compiler` (`lib/std/main.tsp`) and
     * `@typespec/protobuf` (`lib/proto.tsp`).
     */
    private fun entryPointOf(dir: VirtualFile): VirtualFile? {
        val packageJson = dir.findChild("package.json")
        val relPath = packageJson?.let { readEntryPointPath(it) }
        val resolved = relPath?.let { dir.findFileByRelativePath(it.removePrefix("./")) }
        return resolved ?: dir.findChild("main.tsp")
    }

    private fun readEntryPointPath(packageJson: VirtualFile): String? =
        try {
            val json = JsonParser.parseString(VfsUtilCore.loadText(packageJson)).asJsonObject
            exportsTypespecPath(json) ?: stringField(json, "tspMain") ?: stringField(json, "main")
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            // Malformed package.json, unreadable file, or unexpected shape — tolerate, never
            // throw out of a resolve (ADR 0010 D3 / plan 05 M5.6a Approach).
            null
        }

    private fun exportsTypespecPath(json: JsonObject): String? {
        val exports = json.get("exports")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val dot = exports.get(".") ?: return null
        return when {
            dot.isJsonPrimitive && dot.asJsonPrimitive.isString -> dot.asString
            dot.isJsonObject -> stringField(dot.asJsonObject, "typespec")
            else -> null
        }
    }

    private fun stringField(json: JsonObject, name: String): String? =
        json.get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
}
