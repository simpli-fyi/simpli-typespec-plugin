package simpli.fyi.plugins.typespec.stubs

import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.NamedStub
import com.intellij.psi.stubs.NamedStubBase
import com.intellij.psi.stubs.StubElement
import simpli.fyi.plugins.typespec.psi.TypeSpecNamedElement

/**
 * The stub payload for each of the 10 rules `TypeSpec.bnf` marks `stubClass=` (plan 06 M6.5a,
 * ADR 0011 §Architecture in one screen). Deliberately tiny — **name** (backtick-stripped) and
 * the **enclosing** namespace path (a pre-computed dotted string), nothing else. The
 * declaration's *kind* is the element type ([TypeSpecStubTypes]'s ten singletons), never a
 * stored field.
 *
 * [namespacePath] is always computed from the **parent stub chain**
 * ([TypeSpecDeclStubElementType.createStub]), never by walking PSI ancestors — walking PSI here
 * would force an AST load for every stub built, defeating the entire point of a stub tree
 * (ADR 0011 D3).
 *
 * A `namespace_statement` is the one irregular case: `namespace A.B.C;` declares three
 * namespaces with one PSI node, so its stub additionally carries its **own** dotted segments in
 * [ownSegments] — empty for every other stubbed kind.
 */
interface TypeSpecDeclStub : NamedStub<TypeSpecNamedElement> {

    /** The dotted path of the namespace this declaration lives directly under; `""` for global. */
    val namespacePath: String

    /**
     * This stub's own dotted name segments (`["A", "B", "C"]` for `namespace A.B.C;`) — always
     * empty for the other 9 stubbed kinds, which have no dotted name of their own.
     */
    val ownSegments: List<String>
}

class TypeSpecDeclStubImpl(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    private val declaredName: String,
    override val namespacePath: String,
    override val ownSegments: List<String>,
) : NamedStubBase<TypeSpecNamedElement>(parent, elementType, declaredName), TypeSpecDeclStub {

    override fun getName(): String = declaredName
}
