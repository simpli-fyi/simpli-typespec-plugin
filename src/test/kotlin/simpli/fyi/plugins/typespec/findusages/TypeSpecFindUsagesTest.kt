package simpli.fyi.plugins.typespec.findusages

import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import simpli.fyi.plugins.typespec.psi.TypeSpecAliasStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecEnumStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecInterfaceStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecModelStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecNamespaceStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecOpStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecScalarStatement
import simpli.fyi.plugins.typespec.psi.TypeSpecUnionStatement

/**
 * Find Usages suite ([ADR 0004](../../../../../../../../docs/adr/0004-reference-resolution-approach.md)
 * D6, [plan 02](../../../../../../../../docs/plans/02-navigation.md) §
 * `TypeSpecFindUsagesTest`).
 *
 * Registering [TypeSpecFindUsagesProvider] is what turns "Find Usages" on a `.tsp` declaration
 * from "Cannot search for usages" into a real, `ReferencesSearch`-backed query — these tests
 * exercise the provider's own contract (word scanner, type/descriptive-name labelling) plus one
 * end-to-end `testFindUsages` run across two files.
 */
class TypeSpecFindUsagesTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    // ---- end-to-end: usages of a model, across files --------------------------------------

    fun testUsagesOfModelFoundAcrossFiles() {
        val usages = myFixture.testFindUsages("resolve/usages-a.tsp", "resolve/usages-b.tsp")
        // Widget is referenced in usages-a.tsp (SelfUser.w) and twice in usages-b.tsp
        // (Container.a, Container.b) -> 3 usages total.
        assertSize(3, usages)
    }

    fun testDeclarationWithNoUsagesReportsNoneWithoutErroring() {
        val usages = myFixture.testFindUsages("resolve/usages-none.tsp")
        assertEmpty(usages)
    }

    // ---- provider contract: canFindUsagesFor -----------------------------------------------

    fun testCanFindUsagesForEveryDeclarationKind() {
        myFixture.configureByFile("resolve/all-kinds.tsp")
        val provider = TypeSpecFindUsagesProvider()
        val declarations = declarationsIn(myFixture.file)
        assertTrue("expected at least the eight declaration kinds", declarations.size >= 8)
        for (decl in declarations) {
            assertTrue("canFindUsagesFor false for ${decl.javaClass.simpleName}", provider.canFindUsagesFor(decl))
        }
    }

    fun testCanFindUsagesForFalseForKeywordLeaf() {
        myFixture.configureByFile("resolve/all-kinds.tsp")
        val provider = TypeSpecFindUsagesProvider()
        val model = declarationsIn(myFixture.file).filterIsInstance<TypeSpecModelStatement>().first()
        // `model_statement ::= decorator_application* 'model' identifier ...` — with no
        // decorators the first child is the 'model' keyword leaf itself.
        val modelKeywordLeaf = model.firstChild
        assertNotNull("expected the 'model' keyword to be the declaration's first child", modelKeywordLeaf)
        assertEquals("model", modelKeywordLeaf.text)
        assertFalse(provider.canFindUsagesFor(modelKeywordLeaf))
    }

    // ---- provider contract: getType, non-empty for every declaration kind -----------------

    fun testGetTypeNonEmptyForEveryDeclarationKind() {
        myFixture.configureByFile("resolve/all-kinds.tsp")
        val provider = TypeSpecFindUsagesProvider()
        val declarations = declarationsIn(myFixture.file)
        val seenTypes = mutableSetOf<String>()
        for (decl in declarations) {
            val type = provider.getType(decl)
            assertTrue("empty getType() for ${decl.javaClass.simpleName} (missing when-branch?)", type.isNotEmpty())
            seenTypes += type
        }
        assertEquals(
            setOf("model", "enum", "union", "interface", "alias", "scalar", "op", "namespace"),
            seenTypes,
        )
    }

    // ---- provider contract: getDescriptiveName / getNodeText -------------------------------

    fun testGetDescriptiveNameAndNodeTextReturnDeclarationName() {
        myFixture.configureByFile("resolve/same-file-model.tsp")
        val provider = TypeSpecFindUsagesProvider()
        val model = myFixture.findElementByText("Address", TypeSpecModelStatement::class.java)
        assertNotNull(model)
        assertEquals("Address", provider.getDescriptiveName(model!!))
        assertEquals("Address", provider.getNodeText(model, false))
        assertEquals("Address", provider.getNodeText(model, true))
    }

    // ---- helpers ----------------------------------------------------------------------------

    private fun declarationsIn(root: PsiElement): List<PsiElement> {
        val result = mutableListOf<PsiElement>()
        fun visit(el: PsiElement) {
            when (el) {
                is TypeSpecModelStatement, is TypeSpecEnumStatement, is TypeSpecUnionStatement,
                is TypeSpecInterfaceStatement, is TypeSpecAliasStatement, is TypeSpecScalarStatement,
                is TypeSpecOpStatement, is TypeSpecNamespaceStatement,
                -> result.add(el)
            }
            for (child in el.children) visit(child)
        }
        visit(root)
        return result
    }
}
