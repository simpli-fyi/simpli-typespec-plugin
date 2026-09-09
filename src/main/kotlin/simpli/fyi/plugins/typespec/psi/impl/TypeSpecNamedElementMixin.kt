package simpli.fyi.plugins.typespec.psi.impl

import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.search.SearchScope
import com.intellij.psi.stubs.IStubElementType
import com.intellij.util.IncorrectOperationException
import simpli.fyi.plugins.typespec.psi.TypeSpecIdentifier
import simpli.fyi.plugins.typespec.psi.TypeSpecNamedElement
import simpli.fyi.plugins.typespec.psi.TypeSpecPsiUtil
import simpli.fyi.plugins.typespec.resolve.TypeSpecSearchScopes
import simpli.fyi.plugins.typespec.stubs.TypeSpecDeclStub

/**
 * Hand-written base class satisfying [TypeSpecNamedElement] for every declaration rule that
 * carries `mixin("<rule>") = "...psi.impl.TypeSpecNamedElementMixin"` in `TypeSpec.bnf`:
 * `namespace_statement`, `model_statement`, `model_property`, `template_parameter`, and — since
 * M6.5a (plan 06, ADR 0011) — `op_statement`, `interface_statement`, `enum_statement`,
 * `union_statement`, `alias_statement`, `scalar_statement`, `dec_statement`, `fn_statement`
 * ([ADR 0004](../../../../../../../../../docs/adr/0004-reference-resolution-approach.md) D7.2/D7.3,
 * [ADR 0006](../../../../../../../../../docs/adr/0006-grammar-toolchain.md) D7). Grammar-Kit only
 * ever sees this class name as a *string* attribute and emits an `extends` clause from it — it
 * never needs this class on the generator's own classpath (unlike `methods=[...]` /
 * `psiImplUtilClass`, both banned in this repo's `.bnf` files for exactly that reason).
 *
 * All contract methods are implemented directly here, as ordinary Kotlin instance methods —
 * `methods=[...]` is never used, so Grammar-Kit is never asked to know any of them exist.
 *
 * Base class is [StubBasedPsiElementBase] (not `ASTWrapperPsiElement`) since M6.5a: Grammar-Kit
 * generates a `(TypeSpecDeclStub stub, IStubElementType stubType)` constructor on every one of
 * the 10 stubbed rules' `...Impl` classes, calling `super(stub, stubType)` — verified by running
 * the generator standalone against this repo's own `.bnf` (ADR 0011 D5). The 4 rules that are
 * NOT stubbed (`model_property`, `template_parameter`, plus `enum_member`/`union_variant`, which
 * share this mixin too) still only ever get the `(ASTNode)` constructor, which
 * [StubBasedPsiElementBase] also provides.
 */
abstract class TypeSpecNamedElementMixin : StubBasedPsiElementBase<TypeSpecDeclStub>, TypeSpecNamedElement {

    constructor(node: ASTNode) : super(node)
    constructor(stub: TypeSpecDeclStub, stubType: IStubElementType<*, *>) : super(stub, stubType)

    /**
     * Reproduces `ASTWrapperPsiElement`'s exact `toString()` format — bytecode-verified against
     * ideaIC-2025.2.6.3 as `getClass().getSimpleName() + "(" + node.getElementType() + ")"`.
     * Neither `StubBasedPsiElementBase` nor `ASTDelegatePsiElement` (its superclass) declares a
     * `toString()` of its own (also bytecode-verified) — losing this override after switching
     * base classes would silently turn every parser-golden PSI dump line into
     * `simpli.fyi...Impl@1f2e3d`, which is both wrong and non-deterministic across JVM runs
     * (plan 06 M6.5a Approach §4, "the golden-churn trap"; ADR 0011). Deliberately uses
     * [getElementTypeImpl] — protected, works whether or not this element currently has an
     * AST — not `getElementType()`.
     */
    override fun toString(): String = "${javaClass.simpleName}($elementTypeImpl)"

    /**
     * The name's [TypeSpecIdentifier] node — the **last** segment for a dotted
     * `namespace_statement`, the (only) segment for everything else. Navigation, Find Usages
     * previews and the structure view all point here, not at the declaration's keyword.
     */
    override fun getNameIdentifier(): TypeSpecIdentifier? = TypeSpecPsiUtil.findNameIdentifier(this)

    /**
     * Backtick-stripped text of [getNameIdentifier] ([ADR 0004](../../../../../../../../../docs/adr/0004-reference-resolution-approach.md) D7.3).
     * Prefers the stub's own (already backtick-stripped) name when a stub is present — plan 06
     * M6.5a Approach §4: this is what lets a project-wide name lookup answer without ever
     * forcing this element's AST to load.
     */
    override fun getName(): String? = greenStub?.name ?: TypeSpecPsiUtil.stripBackticks(nameIdentifier?.text)

    /**
     * Points navigation/Find Usages/the structure view at the name, not the declaration's
     * leading keyword — getting this wrong trips `ParsingTestCase.checkRangeConsistency`.
     */
    override fun getTextOffset(): Int = nameIdentifier?.textRange?.startOffset ?: super.getTextOffset()

    /**
     * Routes through [ElementManipulators.handleContentChange], the single writer for a
     * [TypeSpecIdentifier] ([ADR 0012](../../../../../../../../../docs/adr/0012-rename-refactoring.md)
     * D4). That resolves the registered `TypeSpecIdentifierManipulator` and calls it with
     * `getRangeInElement(nameIdentifier)` — the full range — so this always takes the
     * full-identifier branch. `this` remains valid afterward: only a child node is swapped, the
     * declaration itself is not replaced.
     */
    override fun setName(name: String): PsiElement {
        val id = nameIdentifier ?: throw IncorrectOperationException("declaration has no name identifier")
        ElementManipulators.handleContentChange(id, name)
        return this
    }

    /**
     * Restricts every usage search rooted at this declaration -- Find Usages and, critically,
     * Rename -- to [TypeSpecSearchScopes.tspScope], the same scope the resolver uses.
     *
     * The default (`PsiElement.getUseScope()` -> whole-project scope) includes `node_modules`,
     * and in an npm-workspace TypeSpec monorepo -- the standard layout -- `node_modules` holds
     * symlinks back into the project's own sources (`node_modules/pkg -> ../model/pkg`). Every
     * project file is then reachable at two paths, so a usage is found twice and Rename rewrites
     * the same file on disk through two different VirtualFiles. That double write is a
     * correctness problem in its own right, and it surfaces to the user as one "File Cache
     * Conflict" dialog per touched file: the write through one path reaches disk, and the VFS
     * reports the twin as changed *not from save*, which is exactly
     * `MemoryDiskConflictResolver`'s trigger.
     *
     * Usages genuinely inside `node_modules` are library sources and must not be rewritten
     * anyway (ADR 0010, ADR 0012 D9).
     */
    override fun getUseScope(): SearchScope = TypeSpecSearchScopes.tspScope(project)

    override fun getPresentation(): ItemPresentation = object : ItemPresentation {
        override fun getPresentableText(): String? = name
        override fun getLocationString(): String? = containingFile?.name
        override fun getIcon(unused: Boolean) = null
    }
}
