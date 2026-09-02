package simpli.fyi.plugins.typespec.psi

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.IncorrectOperationException

/**
 * M5b acceptance (plan 03, ADR 0004 D7): the named-element contract every declaration in scope
 * must satisfy, plus [TypeSpecFile]'s four ADR 0004 D7.4 accessors. Uses `ContractFixture.tsp`
 * under `src/test/testData/psi`, which exercises every declaration kind at once (a namespace, a
 * templated `model` with `extends`/`is`, a plain property, and a backtick-quoted name/property).
 */
class TypeSpecPsiContractTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    private fun configure(): TypeSpecFile =
        myFixture.configureByFile("psi/ContractFixture.tsp") as TypeSpecFile

    fun testNamespaceStatementIsNamedElement() {
        val file = configure()
        val namespace = file.getFileNamespace()
        assertNotNull(namespace)
        assertTrue(namespace is TypeSpecNamedElement)
        // getName() is the LAST dotted segment ("Widgets"), not the whole qualified name —
        // per ADR 0004 D7.3, and getTextOffset() must point there, not at "namespace".
        assertEquals("Widgets", namespace!!.name)
        val nameIdentifier = namespace.nameIdentifier
        assertNotNull(nameIdentifier)
        assertEquals("Widgets", nameIdentifier!!.text)
        assertEquals(nameIdentifier.textRange.startOffset, namespace.textOffset)
        assertTrue(namespace.textOffset > namespace.textRange.startOffset)
    }

    fun testModelStatementIsNamedElementWithBacktickName() {
        val file = configure()
        val model = PsiTreeUtil.findChildOfType(file, TypeSpecModelStatement::class.java)
        assertNotNull(model)
        assertTrue(model is TypeSpecNamedElement)
        // Backtick-quoted name: getName() strips the backticks...
        assertEquals("my-model", model!!.name)
        // ...but the identifier node's own text keeps them (only getName() unquotes).
        val nameIdentifier = model.nameIdentifier
        assertNotNull(nameIdentifier)
        assertEquals("`my-model`", nameIdentifier!!.text)
        assertEquals(nameIdentifier.textRange.startOffset, model.textOffset)
        // textOffset must point at the name, not the leading "model" keyword.
        assertTrue(model.textOffset > model.textRange.startOffset)
    }

    fun testModelPropertyIsNamedElement() {
        val file = configure()
        val properties = PsiTreeUtil.findChildrenOfType(file, TypeSpecModelProperty::class.java).toList()
        assertEquals(2, properties.size)

        val id = properties.first { it.name == "id" }
        assertTrue(id is TypeSpecNamedElement)
        assertEquals("id", id.nameIdentifier!!.text)
        assertEquals(id.nameIdentifier!!.textRange.startOffset, id.textOffset)

        val firstName = properties.first { it.name == "first-name" }
        assertTrue(firstName is TypeSpecNamedElement)
        assertEquals("`first-name`", firstName.nameIdentifier!!.text)
        assertEquals("first-name", firstName.name)
        assertEquals(firstName.nameIdentifier!!.textRange.startOffset, firstName.textOffset)
    }

    fun testTemplateParameterIsNamedElement() {
        val file = configure()
        val templateParameter = PsiTreeUtil.findChildOfType(file, TypeSpecTemplateParameter::class.java)
        assertNotNull(templateParameter)
        assertTrue(templateParameter is TypeSpecNamedElement)
        assertEquals("T", templateParameter!!.name)
        assertEquals(templateParameter.nameIdentifier!!.textRange.startOffset, templateParameter.textOffset)
    }

    fun testSetNameThrowsForEveryNamedElementKind() {
        val file = configure()
        val namedElements = PsiTreeUtil.findChildrenOfType(file, TypeSpecNamedElement::class.java)
        assertTrue(namedElements.isNotEmpty())
        for (element in namedElements) {
            assertThrows(IncorrectOperationException::class.java) { element.setName("renamed") }
        }
    }

    fun testFileAccessors() {
        val file = configure()

        val imports = file.getImportStatements()
        assertEquals(1, imports.size)
        assertEquals("\"./models.tsp\"", imports[0].node.findChildByType(TypeSpecTokenTypes.STRING)?.text)

        val usings = file.getUsingStatements()
        assertEquals(1, usings.size)

        val namespace = file.getFileNamespace()
        assertNotNull(namespace)
        assertEquals("Widgets", namespace!!.name)
        val topLevel = file.getTopLevelDeclarations()
        assertEquals(1, topLevel.size)
        assertSame(namespace, topLevel[0])
    }
}
