package simpli.fyi.plugins.typespec.refactoring

import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.IncorrectOperationException
import simpli.fyi.plugins.typespec.psi.TypeSpecIdentifier
import simpli.fyi.plugins.typespec.psi.TypeSpecNamedElement

/**
 * Plan 07 M6.5g acceptance (docs/plans/07-rename.md, "M6.5g -- The write path: element factory,
 * `setName`, manipulator" -- Acceptance). Tests the mechanism directly, in-memory: no
 * `RenameProcessor`, no UI, no testData fixtures. M6.5h tests the refactoring itself.
 */
class TypeSpecSetNameTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    private fun setNameInWriteAction(element: PsiNamedElement, newName: String) {
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            element.setName(newName)
        }
    }

    // ---- acceptance 1: basic rename ------------------------------------------------------

    fun testSetName_basicModelRename() {
        val file = myFixture.configureByText("main.tsp", "model Foo {}")
        val model = PsiTreeUtil.findChildOfType(file, TypeSpecNamedElement::class.java)!!
        setNameInWriteAction(model, "Bar")

        assertEquals("model Bar {}", file.text)
        assertTrue(model.isValid)
        assertEquals("Bar", model.name)
        assertEquals("Bar", (model as? com.intellij.psi.PsiNameIdentifierOwner)?.nameIdentifier?.text)
    }

    // ---- acceptance 2: escaping to a keyword, and no PsiErrorElement afterwards ----------

    fun testSetName_toKeyword_escapesAndReparsesCleanly() {
        val file = myFixture.configureByText("main.tsp", "model Foo {}")
        val model = PsiTreeUtil.findChildOfType(file, TypeSpecNamedElement::class.java)!!
        setNameInWriteAction(model, "model")

        assertEquals("model `model` {}", file.text)
        assertEquals("model", model.name)
        assertNull(
            "file must re-parse with no PsiErrorElement after escaping",
            PsiTreeUtil.findChildOfType(file, PsiErrorElement::class.java),
        )
    }

    // ---- acceptance 3: renaming a backticked name to a legal bare name drops backticks ---

    fun testSetName_fromBacktickedToLegalBare_dropsBackticks() {
        val file = myFixture.configureByText("main.tsp", "model `model` {}")
        val model = PsiTreeUtil.findChildOfType(file, TypeSpecNamedElement::class.java)!!
        setNameInWriteAction(model, "Foo")

        assertEquals("model Foo {}", file.text)
        assertEquals("Foo", model.name)
    }

    // ---- acceptance 4: renaming to a name with a space escapes with backticks -----------

    fun testSetName_toNameWithSpace_backticked() {
        val file = myFixture.configureByText("main.tsp", "model Foo {}")
        val model = PsiTreeUtil.findChildOfType(file, TypeSpecNamedElement::class.java)!!
        setNameInWriteAction(model, "my name")

        assertEquals("model `my name` {}", file.text)
        assertNull(PsiTreeUtil.findChildOfType(file, PsiErrorElement::class.java))
    }

    // ---- acceptance 5: unrepresentable names throw AND leave the file byte-identical ----

    fun testSetName_backtickInNewName_throwsAndLeavesFileUnchanged() {
        val file = myFixture.configureByText("main.tsp", "model Foo {}")
        val model = PsiTreeUtil.findChildOfType(file, TypeSpecNamedElement::class.java)!!
        val before = file.text

        var threw = false
        try {
            setNameInWriteAction(model, "a`b")
        } catch (_: IncorrectOperationException) {
            threw = true
        }

        assertTrue("expected IncorrectOperationException", threw)
        assertEquals("file text must be byte-identical after a failed setName", before, file.text)
    }

    fun testSetName_empty_throwsAndLeavesFileUnchanged() {
        val file = myFixture.configureByText("main.tsp", "model Foo {}")
        val model = PsiTreeUtil.findChildOfType(file, TypeSpecNamedElement::class.java)!!
        val before = file.text

        var threw = false
        try {
            setNameInWriteAction(model, "")
        } catch (_: IncorrectOperationException) {
            threw = true
        }

        assertTrue("expected IncorrectOperationException", threw)
        assertEquals("file text must be byte-identical after a failed setName", before, file.text)
    }

    fun testSetName_embeddedNewline_throwsAndLeavesFileUnchanged() {
        val file = myFixture.configureByText("main.tsp", "model Foo {}")
        val model = PsiTreeUtil.findChildOfType(file, TypeSpecNamedElement::class.java)!!
        val before = file.text

        var threw = false
        try {
            setNameInWriteAction(model, "a\nb")
        } catch (_: IncorrectOperationException) {
            threw = true
        }

        assertTrue("expected IncorrectOperationException", threw)
        assertEquals("file text must be byte-identical after a failed setName", before, file.text)
    }

    // ---- acceptance 6: one case per remaining named kind ---------------------------------

    fun testSetName_op() {
        val file = myFixture.configureByText("main.tsp", "op targetOp(): string;")
        val op = PsiTreeUtil.findChildOfType(file, TypeSpecNamedElement::class.java)!!
        setNameInWriteAction(op, "Renamed")
        assertEquals("op Renamed(): string;", file.text)
    }

    fun testSetName_interface() {
        val file = myFixture.configureByText("main.tsp", "interface Foo { read(): string; }")
        val iface = PsiTreeUtil.findChildOfType(file, TypeSpecNamedElement::class.java)!!
        setNameInWriteAction(iface, "Renamed")
        assertEquals("interface Renamed { read(): string; }", file.text)
    }

    fun testSetName_enum() {
        val file = myFixture.configureByText("main.tsp", "enum Foo { A, B }")
        val enum = PsiTreeUtil.findChildOfType(file, TypeSpecNamedElement::class.java)!!
        setNameInWriteAction(enum, "Renamed")
        assertEquals("enum Renamed { A, B }", file.text)
    }

    fun testSetName_union() {
        val file = myFixture.configureByText("main.tsp", "union Foo { a: string, b: int32 }")
        val union = PsiTreeUtil.findChildOfType(file, TypeSpecNamedElement::class.java)!!
        setNameInWriteAction(union, "Renamed")
        assertEquals("union Renamed { a: string, b: int32 }", file.text)
    }

    fun testSetName_alias() {
        val file = myFixture.configureByText("main.tsp", "alias Foo = string;")
        val alias = PsiTreeUtil.findChildOfType(file, TypeSpecNamedElement::class.java)!!
        setNameInWriteAction(alias, "Renamed")
        assertEquals("alias Renamed = string;", file.text)
    }

    fun testSetName_scalar() {
        val file = myFixture.configureByText("main.tsp", "scalar Foo extends string;")
        val scalar = PsiTreeUtil.findChildOfType(file, TypeSpecNamedElement::class.java)!!
        setNameInWriteAction(scalar, "Renamed")
        assertEquals("scalar Renamed extends string;", file.text)
    }

    fun testSetName_dec() {
        val file = myFixture.configureByText("main.tsp", "extern dec Foo(target: unknown);")
        val dec = PsiTreeUtil.findChildOfType(file, TypeSpecNamedElement::class.java)!!
        setNameInWriteAction(dec, "Renamed")
        assertEquals("extern dec Renamed(target: unknown);", file.text)
    }

    fun testSetName_fn() {
        val file = myFixture.configureByText("main.tsp", "extern fn Foo(target: unknown): string;")
        val fn = PsiTreeUtil.findChildOfType(file, TypeSpecNamedElement::class.java)!!
        setNameInWriteAction(fn, "Renamed")
        assertEquals("extern fn Renamed(target: unknown): string;", file.text)
    }

    fun testSetName_modelProperty() {
        // Proves TypeSpecPsiUtil.findNameIdentifier picks the *name* (x), not the *type* (string).
        val file = myFixture.configureByText("main.tsp", "model Foo { x: string; }")
        val property = PsiTreeUtil.findChildOfType(file, simpli.fyi.plugins.typespec.psi.TypeSpecModelProperty::class.java)!!
        setNameInWriteAction(property as PsiNamedElement, "Renamed")
        assertEquals("model Foo { Renamed: string; }", file.text)
    }

    fun testSetName_enumMember() {
        val file = myFixture.configureByText("main.tsp", "enum Foo { A, B }")
        val member = PsiTreeUtil.findChildOfType(file, simpli.fyi.plugins.typespec.psi.TypeSpecEnumMember::class.java)!!
        setNameInWriteAction(member as PsiNamedElement, "Renamed")
        assertEquals("enum Foo { Renamed, B }", file.text)
    }

    fun testSetName_templateParameter() {
        val file = myFixture.configureByText("main.tsp", "model Foo<T> { x: T; }")
        val param = PsiTreeUtil.findChildOfType(
            file,
            simpli.fyi.plugins.typespec.psi.TypeSpecTemplateParameter::class.java,
        )!!
        setNameInWriteAction(param as PsiNamedElement, "Renamed")
        // Only the declaration site is rewritten -- rename usages is out of scope here (M6.5g
        // tests the write path mechanism, not reference rewriting).
        assertEquals("model Foo<Renamed> { x: T; }", file.text)
    }

    // ---- acceptance 7: dotted namespace, only the last segment rewritten ----------------

    fun testSetName_dottedNamespace_onlyLastSegmentRewritten() {
        val file = myFixture.configureByText("main.tsp", "namespace A.B.C;")
        val namespace = PsiTreeUtil.findChildOfType(
            file,
            simpli.fyi.plugins.typespec.psi.TypeSpecNamespaceStatement::class.java,
        )!!
        setNameInWriteAction(namespace as PsiNamedElement, "Z")
        assertEquals("namespace A.B.Z;", file.text)
    }

    // ---- acceptance 8: manipulator partial-range splice, called directly ----------------

    fun testHandleContentChange_partialRange_splicesThenEscapes() {
        val file = myFixture.configureByText("main.tsp", "model Foo {}")
        val model = PsiTreeUtil.findChildOfType(file, TypeSpecNamedElement::class.java)!!
        val id = PsiTreeUtil.findChildOfType(model, TypeSpecIdentifier::class.java)!!
        assertEquals("Foo", id.text)

        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            ElementManipulators.getManipulator(id).handleContentChange(id, TextRange(0, 1), "B")
        }

        assertEquals("model Boo {}", file.text)
    }

    // ---- addition (beyond the plan's letter): the defensive require() bounds check ------

    fun testHandleContentChange_outOfRangeArgument_throwsIllegalArgument() {
        val file = myFixture.configureByText("main.tsp", "model Foo {}")
        val model = PsiTreeUtil.findChildOfType(file, TypeSpecNamedElement::class.java)!!
        val id = PsiTreeUtil.findChildOfType(model, TypeSpecIdentifier::class.java)!!

        var threw = false
        try {
            com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
                ElementManipulators.getManipulator(id).handleContentChange(id, TextRange(0, 999), "B")
            }
        } catch (_: IllegalArgumentException) {
            threw = true
        }
        assertTrue("expected IllegalArgumentException for an out-of-range TextRange", threw)
    }

    // ---- acceptance 9: regression -- run via `./gradlew test`, asserted by the harness --
}
