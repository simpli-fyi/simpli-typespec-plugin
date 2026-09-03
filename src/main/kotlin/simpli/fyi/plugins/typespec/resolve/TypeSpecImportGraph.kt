package simpli.fyi.plugins.typespec.resolve

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import simpli.fyi.plugins.typespec.psi.TypeSpecFile
import simpli.fyi.plugins.typespec.psi.TypeSpecImportStatement

/**
 * Tier B ([ADR 0004](../../../../../../../../docs/adr/0004-reference-resolution-approach.md)
 * D2, [plan 02](../../../../../../../../docs/plans/02-navigation.md)). Turns
 * `import "./x.tsp";` into files.
 *
 * Only relative paths (`./`, `../`) are followed for *explicit* imports — a bare specifier
 * (`import "@typespec/rest";`) resolves via [TypeSpecImportResolver] like any other (ADR 0010).
 * Cycle-safe via a visited [VirtualFile] set; TypeSpec allows import cycles. Capped at
 * [CLOSURE_CAP] files, defensively.
 *
 * The closure also always includes the implicit `@typespec/compiler` standard-library entry
 * point ([plan 06](../../../../../../../../docs/plans/06-stub-index.md) M5.6g,
 * [ADR 0010](../../../../../../../../docs/adr/0010-library-import-resolution.md) open question
 * 1) — the real compiler loads it whether or not a file imports anything, which is why `@doc`,
 * `@key` and the rest of the std-library decorators are unresolved without this. Resolved via
 * the same [TypeSpecImportResolver] used for explicit bare specifiers — no hardcoded path. When
 * `@typespec/compiler` is not installed (no `node_modules`, or no such package), the resolver
 * returns `null` and nothing is seeded — silent, no error (ADR 0010 D5).
 *
 * It also always includes `lib/intrinsics.tsp` from the same package (plan 06 M5.6g' gap 2) —
 * `string`/`int32`/`boolean`/`float`/... live there, not in `lib/std/main.tsp`'s own import
 * closure, and the real compiler loads it out-of-band via its own package root, not through
 * `lib/std/main.tsp` or any `exports` entry. See [INTRINSICS_RELATIVE_PATH]'s doc for the exact
 * upstream citation. Same silent-degrade contract as the std-library edge.
 *
 * Cached per file with a dependency on [PsiModificationTracker.MODIFICATION_COUNT] —
 * deliberately asymmetric with [TypeSpecFileDeclarations]'s per-file dependency: the import
 * graph is small, changes rarely, and correctness on file rename/creation matters more than
 * cache hit rate here (plan 02 "Files to create" § `TypeSpecImportGraph.kt`).
 */
object TypeSpecImportGraph {

    const val CLOSURE_CAP = 200

    /** The bare specifier the TypeSpec compiler implicitly loads for every file. */
    private const val STD_LIBRARY_SPECIFIER = "@typespec/compiler"

    /**
     * `string`/`int32`/`boolean`/`float`/... live here, not in `lib/std/main.tsp`'s import
     * closure. The real compiler loads this file out-of-band, addressed relative to its own
     * package root (`host.getExecutionRoot()`) — never through `package.json`'s `exports` map,
     * which has no entry for it at all (verified against `program.js`'s `loadIntrinsicTypes` and
     * `node-host.js`'s `getExecutionRoot` in `@typespec/compiler`). Because there genuinely is no
     * package-exposed entry point to resolve this through, this one relative path is hardcoded,
     * isolated to this single named constant, exactly as `TypeSpecImportResolver.entryPointOf`'s
     * doc comment does for its own "verified, not guessed" upstream paths.
     */
    private const val INTRINSICS_RELATIVE_PATH = "lib/intrinsics.tsp"

    /**
     * The transitive closure of [file]'s `import` statements plus the implicit std-library edge,
     * including [file] itself.
     */
    fun transitiveClosure(file: TypeSpecFile): Set<TypeSpecFile> =
        CachedValuesManager.getCachedValue(file) {
            CachedValueProvider.Result.create(compute(file), PsiModificationTracker.MODIFICATION_COUNT)
        }

    private fun compute(file: TypeSpecFile): Set<TypeSpecFile> {
        val visited = mutableSetOf<VirtualFile>()
        val result = mutableSetOf<TypeSpecFile>()
        val queue = ArrayDeque<TypeSpecFile>()

        file.virtualFile?.let { visited.add(it) }
        result.add(file)
        queue.add(file)

        // Implicit std-library edge (plan 06 M5.6g) — seeded once, from the starting file only,
        // exactly like an `import "@typespec/compiler";` the user never has to write. Absent or
        // unresolvable degrades to seeding nothing.
        val stdLibrary = TypeSpecImportResolver.resolve(file, STD_LIBRARY_SPECIFIER)
        val stdLibraryFile = stdLibrary?.virtualFile
        if (stdLibrary != null && stdLibraryFile != null && visited.add(stdLibraryFile)) {
            result.add(stdLibrary)
            queue.add(stdLibrary)
        }

        // Implicit intrinsics edge (plan 06 M5.6g' gap 2) — resolved through the package root
        // TypeSpecImportResolver.resolvePackageDir already locates for @typespec/compiler, not a
        // hardcoded absolute or project-relative path. Absent/unresolvable degrades to seeding
        // nothing, same as the std-library edge above.
        val stdLibraryPackageDir = TypeSpecImportResolver.resolvePackageDir(file, STD_LIBRARY_SPECIFIER)
        val intrinsicsVirtualFile = stdLibraryPackageDir
            ?.findFileByRelativePath(INTRINSICS_RELATIVE_PATH)
            ?.canonicalFile
            ?.takeIf { !it.isDirectory && it.extension == "tsp" }
        if (intrinsicsVirtualFile != null && visited.add(intrinsicsVirtualFile)) {
            val intrinsicsFile = PsiManager.getInstance(file.project).findFile(intrinsicsVirtualFile) as? TypeSpecFile
            if (intrinsicsFile != null) {
                result.add(intrinsicsFile)
                queue.add(intrinsicsFile)
            }
        }

        while (queue.isNotEmpty() && result.size < CLOSURE_CAP) {
            ProgressManager.checkCanceled()
            val current = queue.removeFirst()
            for (importStatement in current.getImportStatements()) {
                val target = resolveImportTarget(current, importStatement) ?: continue
                val virtualFile = target.virtualFile ?: continue
                if (visited.add(virtualFile)) {
                    result.add(target)
                    queue.add(target)
                    if (result.size >= CLOSURE_CAP) break
                }
            }
        }
        return result
    }

    private fun resolveImportTarget(file: TypeSpecFile, statement: TypeSpecImportStatement): TypeSpecFile? {
        val rawText = statement.string?.text ?: return null
        val path = rawText.trim('"')
        // ADR 0010 (plan 05 M5.6a): both relative and bare (library) specifiers now resolve,
        // via the shared entry-point rule that also backs TypeSpecImportReference (M5.6b) — no
        // more startsWith("./") bail-out and no more hardcoded findChild("main.tsp").
        return TypeSpecImportResolver.resolve(file, path)
    }
}
