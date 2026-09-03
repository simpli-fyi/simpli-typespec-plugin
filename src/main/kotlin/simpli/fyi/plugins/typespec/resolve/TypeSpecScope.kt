package simpli.fyi.plugins.typespec.resolve

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.util.PsiTreeUtil
import simpli.fyi.plugins.typespec.psi.TypeSpecFile
import simpli.fyi.plugins.typespec.psi.TypeSpecNamespaceStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecPsiUtil
import simpli.fyi.plugins.typespec.psi.TypeSpecUsingStatement

/**
 * A dot-separated namespace path. Empty segments == the global namespace.
 */
@JvmInline
value class NamespacePath(val segments: List<String>) {
    override fun toString(): String = if (segments.isEmpty()) "<global>" else segments.joinToString(".")
}

/**
 * Pure, PSI-only, no I/O. Turns a position into an ordered chain of namespace paths, and turns
 * a `using` binding into the namespace path it names ([ADR 0004](../../../../../../../../docs/adr/0004-reference-resolution-approach.md)
 * F4, [plan 02](../../../../../../../../docs/plans/02-navigation.md)).
 */
object TypeSpecScope {

    /**
     * The ordered scope chain for [element], innermost enclosing namespace first, then each of
     * its dotted (or nested-block) ancestors, then global — every prefix of [pathOf], longest
     * first.
     */
    fun chainFor(element: PsiElement): List<NamespacePath> = prefixChain(pathOf(element))

    /**
     * Where [element] lives: the concatenation of every enclosing `namespace` statement's own
     * (dotted) name, outermost first — NOT including [element] itself even when it is a
     * `namespace` statement (that is [fullPathOf]).
     */
    fun pathOf(element: PsiElement): NamespacePath {
        val ancestors = mutableListOf<TypeSpecNamespaceStatement>()
        var cur = element.parent
        while (cur != null) {
            if (cur is TypeSpecNamespaceStatement) {
                ancestors.add(cur)
            }
            cur = cur.parent
        }
        ancestors.reverse() // outermost first
        val segments = mutableListOf<String>()
        for (ns in ancestors) segments += segmentsOf(ns)
        return NamespacePath(segments)
    }

    /** The full path a `namespace` declaration itself denotes, including its own dotted name. */
    fun fullPathOf(namespace: TypeSpecNamespaceStatement): NamespacePath =
        NamespacePath(pathOf(namespace).segments + segmentsOf(namespace))

    /** A namespace statement's own dotted name segments, backtick-stripped. */
    fun segmentsOf(namespace: TypeSpecNamespaceStatement): List<String> =
        namespace.qualifiedName?.identifierList
            ?.map { TypeSpecPsiUtil.stripBackticks(it.text) ?: it.text }
            .orEmpty()

    /**
     * The namespace targets of every `using` statement declared directly in the namespace
     * (or file root) at [path], within [file]. Resolves each `using`'s own qualified name
     * through the *same* per-segment [simpli.fyi.plugins.typespec.psi.TypeSpecIdentifier]
     * reference the rest of the resolver uses — which naturally walks the chain
     * longest-prefix-first from the point where the `using` appears (ADR 0004 D7/F4).
     */
    fun usingsVisibleIn(path: NamespacePath, file: TypeSpecFile): List<NamespacePath> {
        val containers = TypeSpecFileDeclarations.of(file).containersFor(path)
        return containers.flatMap { usingsIn(it) }.mapNotNull { resolveUsingTarget(it) }
    }

    private fun usingsIn(container: PsiElement): List<TypeSpecUsingStatement> = when (container) {
        is TypeSpecFile -> container.getUsingStatements()
        else -> PsiTreeUtil.getChildrenOfTypeAsList(container, TypeSpecUsingStatement::class.java)
    }

    // Re-entrancy guard, per thread: resolving a `using`'s own target can legitimately walk
    // back through `usingsVisibleIn` at the SAME scope the `using` statement itself is
    // declared in (that scope's using list necessarily includes this very statement) — the
    // ordinary case (`namespace MyOrg; using Common;`, resolving "Common") hits this on the
    // very first call, not just some pathological input. `ResolveCache`'s own recursion guard
    // does not catch it here because `getReference()` mints a fresh `TypeSpecReference`
    // instance on every call (ADR 0004 D1), so identity-based re-entrancy detection in the
    // cache never sees "the same reference" twice. Guarding here, on the PSI `using_statement`
    // itself (stable identity), is what actually breaks the cycle — verified against a
    // StackOverflowError this guard's absence produced in M5.5 self-verification.
    private val resolvingUsings = ThreadLocal.withInitial { mutableSetOf<TypeSpecUsingStatement>() }

    private fun resolveUsingTarget(using: TypeSpecUsingStatement): NamespacePath? {
        val inProgress = resolvingUsings.get()
        if (!inProgress.add(using)) return null
        try {
            val lastSegment = using.qualifiedName?.identifierList?.lastOrNull() ?: return null
            val target = (lastSegment.reference as? PsiPolyVariantReference)
                ?.multiResolve(false)
                ?.mapNotNull { it.element as? TypeSpecNamespaceStatement }
                ?.firstOrNull()
                ?: return null
            return fullPathOf(target)
        } finally {
            inProgress.remove(using)
        }
    }

    private fun prefixChain(path: NamespacePath): List<NamespacePath> =
        (path.segments.size downTo 0).map { len -> NamespacePath(path.segments.subList(0, len)) }
}
