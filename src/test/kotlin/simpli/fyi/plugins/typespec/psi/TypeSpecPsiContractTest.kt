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

    private fun configureM5c(): TypeSpecFile =
        myFixture.configureByFile("psi/ContractFixtureM5c.tsp") as TypeSpecFile

    /**
     * M5c acceptance (plan 03, ADR 0004 D7.2): the named-element contract extended to
     * op, interface, enum + enum member, union + union variant, alias, scalar. Uses a
     * separate `ContractFixtureM5c.tsp` fixture (M5b's `ContractFixture.tsp` — and its
     * existing tests/assertions above, e.g. `testFileAccessors`'s
     * `getTopLevelDeclarations().size == 1` — are untouched).
     */
    fun testOpStatementIsNamedElement() {
        val file = configureM5c()
        val op = PsiTreeUtil.findChildOfType(file, TypeSpecOpStatement::class.java)
        assertNotNull(op)
        assertTrue(op is TypeSpecNamedElement)
        assertEquals("read", op!!.name)
        val nameIdentifier = op.nameIdentifier
        assertNotNull(nameIdentifier)
        assertEquals("read", nameIdentifier!!.text)
        assertEquals(nameIdentifier.textRange.startOffset, op.textOffset)
        assertTrue(op.textOffset > op.textRange.startOffset)
    }

    fun testInterfaceStatementIsNamedElement() {
        val file = configureM5c()
        val iface = PsiTreeUtil.findChildOfType(file, TypeSpecInterfaceStatement::class.java)
        assertNotNull(iface)
        assertTrue(iface is TypeSpecNamedElement)
        assertEquals("Store", iface!!.name)
        val nameIdentifier = iface.nameIdentifier
        assertNotNull(nameIdentifier)
        assertEquals(nameIdentifier!!.textRange.startOffset, iface.textOffset)
        assertTrue(iface.textOffset > iface.textRange.startOffset)
    }

    fun testEnumStatementAndMemberAreNamedElements() {
        val file = configureM5c()
        val enum = PsiTreeUtil.findChildOfType(file, TypeSpecEnumStatement::class.java)
        assertNotNull(enum)
        assertTrue(enum is TypeSpecNamedElement)
        assertEquals("Color", enum!!.name)
        assertEquals(enum.nameIdentifier!!.textRange.startOffset, enum.textOffset)
        assertTrue(enum.textOffset > enum.textRange.startOffset)

        val member = PsiTreeUtil.findChildOfType(file, TypeSpecEnumMember::class.java)
        assertNotNull(member)
        assertTrue(member is TypeSpecNamedElement)
        assertEquals("Red", member!!.name)
        assertEquals(member.nameIdentifier!!.textRange.startOffset, member.textOffset)
        // No leading keyword before an enum member's name (`identifier ':' value_expression`),
        // unlike `enum`/`union`/`op`/etc. themselves — textOffset equals the element's own
        // start here, it is never *greater than* it.
        assertEquals(member.textRange.startOffset, member.textOffset)
    }

    fun testUnionStatementAndVariantAreNamedElements() {
        val file = configureM5c()
        val union = PsiTreeUtil.findChildOfType(file, TypeSpecUnionStatement::class.java)
        assertNotNull(union)
        assertTrue(union is TypeSpecNamedElement)
        assertEquals("Shape", union!!.name)
        assertEquals(union.nameIdentifier!!.textRange.startOffset, union.textOffset)
        assertTrue(union.textOffset > union.textRange.startOffset)

        val variant = PsiTreeUtil.findChildOfType(file, TypeSpecUnionVariant::class.java)
        assertNotNull(variant)
        assertTrue(variant is TypeSpecNamedElement)
        assertEquals("circle", variant!!.name)
        assertEquals(variant.nameIdentifier!!.textRange.startOffset, variant.textOffset)
        // Same reasoning as `enum_member` above: no leading keyword before a union
        // variant's name (`identifier ':' type_expression_`).
        assertEquals(variant.textRange.startOffset, variant.textOffset)
    }

    fun testAliasStatementIsNamedElement() {
        val file = configureM5c()
        val alias = PsiTreeUtil.findChildOfType(file, TypeSpecAliasStatement::class.java)
        assertNotNull(alias)
        assertTrue(alias is TypeSpecNamedElement)
        assertEquals("Greeting", alias!!.name)
        assertEquals(alias.nameIdentifier!!.textRange.startOffset, alias.textOffset)
        assertTrue(alias.textOffset > alias.textRange.startOffset)
    }

    fun testScalarStatementIsNamedElement() {
        val file = configureM5c()
        val scalar = PsiTreeUtil.findChildOfType(file, TypeSpecScalarStatement::class.java)
        assertNotNull(scalar)
        assertTrue(scalar is TypeSpecNamedElement)
        assertEquals("CustomStr", scalar!!.name)
        assertEquals(scalar.nameIdentifier!!.textRange.startOffset, scalar.textOffset)
        assertTrue(scalar.textOffset > scalar.textRange.startOffset)
    }

    fun testSetNameThrowsForEveryM5cNamedElementKind() {
        val file = configureM5c()
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
