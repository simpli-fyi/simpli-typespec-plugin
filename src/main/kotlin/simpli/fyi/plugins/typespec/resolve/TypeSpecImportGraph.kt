package simpli.fyi.plugins.typespec.resolve

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.vfs.VirtualFile
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
