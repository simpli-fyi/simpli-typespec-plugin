package simpli.fyi.plugins.typespec.resolve

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import simpli.fyi.plugins.typespec.psi.TypeSpecFile
import simpli.fyi.plugins.typespec.psi.TypeSpecNamedElement
import simpli.fyi.plugins.typespec.psi.TypeSpecNamespaceStatement

/**
 * The per-file cached declaration table — the performance centre of the resolver
 * ([ADR 0004](../../../../../../../../docs/adr/0004-reference-resolution-approach.md) D2,
 * [plan 02](../../../../../../../../docs/plans/02-navigation.md)).
 *
 * Built once per file by walking [TypeSpecFile]'s (and each nested `namespace` statement's)
 * *direct* [TypeSpecNamedElement] children — `model`/`op`/`interface`/`enum`/`union`/`alias`/
 * `scalar`/`namespace` — recursing into namespace blocks only. Members of a container that is
 * not itself a namespace (a `model` property, an `enum` member, a `union` variant, an
 * `interface` operation) are deliberately NOT indexed here — resolving `Foo.bar` as a member is
 * out of scope for this milestone (ADR 0004 D6/plan 02 "What this milestone does not do").
 *
 * The cache dependency is [file] itself, deliberately — NOT
 * `PsiModificationTracker.MODIFICATION_COUNT`, which would invalidate every file's table on
 * every keystroke anywhere in the project (ADR 0004 D2). Editing one file invalidates only that
 * file's table.
 */
class TypeSpecFileDeclarations private constructor(
    private val byName: Map<String, List<Pair<NamespacePath, TypeSpecNamedElement>>>,
    private val containersByPath: Map<NamespacePath, List<PsiElement>>,
) {

    /** Declarations named [name] whose containing namespace path is exactly [path]. */
    fun find(name: String, path: NamespacePath): List<TypeSpecNamedElement> =
        byName[name].orEmpty().filter { it.first == path }.map { it.second }

    /** Cheap gate: does this file's text contain any declaration named [name] at all? */
    fun containsName(name: String): Boolean = byName.containsKey(name)

    /**
     * The PSI containers (the file itself for the global path, or a `namespace` statement) in
     * this file whose own full namespace path is exactly [path] — used to find the `using`
     * statements declared directly at that scope.
     */
    fun containersFor(path: NamespacePath): List<PsiElement> = containersByPath[path].orEmpty()

    companion object {
        fun of(file: TypeSpecFile): TypeSpecFileDeclarations =
            CachedValuesManager.getCachedValue(file) {
                CachedValueProvider.Result.create(build(file), file)
            }

        private fun build(file: TypeSpecFile): TypeSpecFileDeclarations {
            val byName = mutableMapOf<String, MutableList<Pair<NamespacePath, TypeSpecNamedElement>>>()
            val containers = mutableMapOf<NamespacePath, MutableList<PsiElement>>()
            containers.getOrPut(NamespacePath(emptyList())) { mutableListOf() }.add(file)

            fun walk(container: PsiElement, path: NamespacePath) {
                for (child in PsiTreeUtil.getChildrenOfTypeAsList(container, TypeSpecNamedElement::class.java)) {
                    ProgressManager.checkCanceled()
                    if (child is TypeSpecNamespaceStatement) {
                        // A dotted namespace declaration (`namespace A.B.C;`) is sugar for
                        // nesting: it declares `A`, `A.B` and `A.B.C`. There is no standalone
                        // declaration of the intermediate segments `A`/`A.B` anywhere in the
                        // file, so every segment is indexed here under its own prefix path,
                        // pointing at this same statement — the nearest thing to a declaration
                        // site those virtual segments have. This also makes overlapping dotted
                        // prefixes across files (`A.B.C` in one, `A.B.D` in another) behave like
                        // any other reopened namespace: resolving `A` yields one candidate per
                        // statement that establishes that prefix, exactly as resolving a
                        // shared, non-dotted namespace name already does.
                        var prefix = path
                        for (segment in TypeSpecScope.segmentsOf(child)) {
                            byName.getOrPut(segment) { mutableListOf() }.add(prefix to child)
                            prefix = NamespacePath(prefix.segments + segment)
                        }
                        val childPath = NamespacePath(path.segments + TypeSpecScope.segmentsOf(child))
                        containers.getOrPut(childPath) { mutableListOf() }.add(child)
                        walk(child, childPath)
                    } else {
                        val name = child.name ?: continue
                        byName.getOrPut(name) { mutableListOf() }.add(path to child)
                    }
                }
            }
            walk(file, NamespacePath(emptyList()))
            return TypeSpecFileDeclarations(byName, containers)
        }
    }
}
