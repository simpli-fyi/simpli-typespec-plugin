package simpli.fyi.plugins.typespec.stubs

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.IndexSink
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import simpli.fyi.plugins.typespec.TypeSpecLanguage
import simpli.fyi.plugins.typespec.psi.TypeSpecNamedElement
import simpli.fyi.plugins.typespec.psi.TypeSpecNamespaceStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecPsiUtil
import simpli.fyi.plugins.typespec.psi.impl.TypeSpecAliasStatementImpl
import simpli.fyi.plugins.typespec.psi.impl.TypeSpecDecStatementImpl
import simpli.fyi.plugins.typespec.psi.impl.TypeSpecEnumStatementImpl
import simpli.fyi.plugins.typespec.psi.impl.TypeSpecFnStatementImpl
import simpli.fyi.plugins.typespec.psi.impl.TypeSpecInterfaceStatementImpl
import simpli.fyi.plugins.typespec.psi.impl.TypeSpecModelStatementImpl
import simpli.fyi.plugins.typespec.psi.impl.TypeSpecNamespaceStatementImpl
import simpli.fyi.plugins.typespec.psi.impl.TypeSpecOpStatementImpl
import simpli.fyi.plugins.typespec.psi.impl.TypeSpecScalarStatementImpl
import simpli.fyi.plugins.typespec.psi.impl.TypeSpecUnionStatementImpl
import simpli.fyi.plugins.typespec.resolve.TypeSpecScope

/**
 * One [IStubElementType] class, instantiated 10 times by [TypeSpecStubTypes] — one singleton
 * per stubbed rule, distinguished only by [getDebugName] (plan 06 M6.5a). Grammar-Kit generates
 * a real two-argument constructor on every stubbed rule's `...Impl` class
 * (`(TypeSpecDeclStub stub, IStubElementType stubType)`, verified by running the generator
 * standalone against this repo's own `.bnf` — ADR 0011 D5), so [createPsi] switches on the
 * debug name to pick the matching concrete impl rather than using reflection.
 */
class TypeSpecDeclStubElementType(debugName: String) :
    IStubElementType<TypeSpecDeclStub, TypeSpecNamedElement>(debugName, TypeSpecLanguage.INSTANCE) {

    /**
     * The rule's own debug name (`"MODEL_STATEMENT"`, …), read via `super.toString()`
     * ([IElementType.toString] returns its own `myDebugName` field verbatim) rather than a
     * field of this class. Two independent reasons, both load-bearing:
     *
     * 1. `IElementType.getDebugName()` is `@ApiStatus.Internal` — calling it directly from this
     *    class's own bytecode fails `verifyPlugin`'s `INTERNAL_API_USAGES` check, whereas calling
     *    the public, non-internal `toString()` (which happens to delegate to it *inside platform
     *    code*, not ours) does not.
     * 2. A field declared on *this* leaf class would not yet be assigned the first time it is
     *    needed: `IStubElementType`'s own constructor calls `getExternalId()` polymorphically
     *    (via `checkNotInstantiatedTooLate`) before this class's own constructor body/field
     *    initializers run — verified empirically (a `private val` here read back `"tsp.null"` at
     *    that exact call site). `IElementType.myDebugName`, by contrast, is set by the
     *    *grandparent* constructor, which has already returned by then.
     */
    private fun ownDebugName(): String = super.toString()

    /**
     * Reproduces [simpli.fyi.plugins.typespec.psi.TypeSpecElementType]'s own `toString()`
     * exactly (`"TypeSpec:" + debugName`) — the golden-churn trap (plan 06 M6.5a Approach §4):
     * every non-stubbed composite element type still prints this way, and
     * [simpli.fyi.plugins.typespec.psi.impl.TypeSpecNamedElementMixin]'s own `toString()`
     * override embeds this type's `toString()` verbatim in every parser-golden PSI dump line.
     * Losing this prefix here (even though it is not required by the stub API itself) is exactly
     * the kind of one-line omission that produces zero test failures and a wrong, unfixable
     * golden.
     */
    override fun toString(): String = "TypeSpec:${ownDebugName()}"

    override fun getExternalId(): String = "tsp.${ownDebugName()}"

    /**
     * `false` for a declaration with no name (broken/error-recovery source) — an unnamed node
     * never enters the stub tree, so it can never enter the index either (ADR 0011 D6 item 4: a
     * stub-version bump).
     */
    override fun shouldCreateStub(node: ASTNode): Boolean {
        val psi = node.psi as? TypeSpecNamedElement ?: return false
        return psi.name != null
    }

    override fun createStub(psi: TypeSpecNamedElement, parentStub: StubElement<out PsiElement>?): TypeSpecDeclStub {
        val name = TypeSpecPsiUtil.stripBackticks(psi.name) ?: ""
        val ownSegments =
            if (psi is TypeSpecNamespaceStatement) TypeSpecScope.segmentsOf(psi) else emptyList()
        val namespacePath = (parentStub as? TypeSpecDeclStub)?.let { parentNamespace ->
            if (parentNamespace.namespacePath.isEmpty()) {
                parentNamespace.ownSegments.joinToString(".")
            } else {
                "${parentNamespace.namespacePath}.${parentNamespace.ownSegments.joinToString(".")}"
            }
        }.orEmpty()
        return TypeSpecDeclStubImpl(parentStub, this, name, namespacePath, ownSegments)
    }

    override fun createPsi(stub: TypeSpecDeclStub): TypeSpecNamedElement = when (ownDebugName()) {
        "NAMESPACE_STATEMENT" -> TypeSpecNamespaceStatementImpl(stub, this)
        "MODEL_STATEMENT" -> TypeSpecModelStatementImpl(stub, this)
        "OP_STATEMENT" -> TypeSpecOpStatementImpl(stub, this)
        "INTERFACE_STATEMENT" -> TypeSpecInterfaceStatementImpl(stub, this)
        "ENUM_STATEMENT" -> TypeSpecEnumStatementImpl(stub, this)
        "UNION_STATEMENT" -> TypeSpecUnionStatementImpl(stub, this)
        "ALIAS_STATEMENT" -> TypeSpecAliasStatementImpl(stub, this)
        "SCALAR_STATEMENT" -> TypeSpecScalarStatementImpl(stub, this)
        "DEC_STATEMENT" -> TypeSpecDecStatementImpl(stub, this)
        "FN_STATEMENT" -> TypeSpecFnStatementImpl(stub, this)
        else -> error("Unknown TypeSpec stub element type: ${ownDebugName()}")
    }

    override fun serialize(stub: TypeSpecDeclStub, dataStream: StubOutputStream) {
        dataStream.writeName(stub.name)
        dataStream.writeName(stub.namespacePath)
        dataStream.writeVarInt(stub.ownSegments.size)
        for (segment in stub.ownSegments) dataStream.writeName(segment)
    }

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): TypeSpecDeclStub {
        val name = dataStream.readNameString().orEmpty()
        val namespacePath = dataStream.readNameString().orEmpty()
        val count = dataStream.readVarInt()
        val ownSegments = (0 until count).map { dataStream.readNameString().orEmpty() }
        return TypeSpecDeclStubImpl(parentStub, this, name, namespacePath, ownSegments)
    }

    /**
     * One occurrence per name a lookup could reasonably ask for (plan 06 M6.5b). For 9 of the 10
     * stubbed kinds that is just [TypeSpecDeclStub.name]. A `namespace_statement` is the
     * irregular case: `namespace A.B.C;` must be findable by each of `A`, `B` and `C` — the same
     * segments [TypeSpecFileDeclarations] indexes a namespace under in the PSI-only path — so a
     * stub with non-empty [TypeSpecDeclStub.ownSegments] is indexed once per segment instead of
     * once under [TypeSpecDeclStub.name] (its own `getName()` already returns the same string as
     * `ownSegments.last()` for a namespace, so indexing both would just duplicate the last
     * segment's occurrence).
     */
    override fun indexStub(stub: TypeSpecDeclStub, sink: IndexSink) {
        if (stub.ownSegments.isNotEmpty()) {
            for (segment in stub.ownSegments) sink.occurrence(TypeSpecDeclarationNameIndex.KEY, segment)
        } else {
            sink.occurrence(TypeSpecDeclarationNameIndex.KEY, stub.name.orEmpty())
        }
    }
}
