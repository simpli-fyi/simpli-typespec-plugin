package simpli.fyi.plugins.typespec.editor

import com.intellij.lang.LanguageCommenters
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import simpli.fyi.plugins.typespec.TypeSpecLanguage

/**
 * M4 acceptance: [TypeSpecCommenter] — `//` line comments and `/* */` block comments.
 *
 * PSI-driven (ADR 0003 F5 was wrong; corrected by ADR 0005): `performEditorAction` drives the
 * real `CommentByLineCommentAction` / `CommentByBlockCommentAction` handlers, which resolve
 * the commenter through `LanguageCommenters.forLanguage(file.getLanguage())`. That requires
 * `TypeSpecParserDefinition` (ADR 0005) so `file.getLanguage()` is actually `TypeSpec`.
 */
class TypeSpecCommenterTest : BasePlatformTestCase() {

    // ---- registration -----------------------------------------------------------

    fun testCommenterIsRegisteredForTypeSpecLanguage() {
        val commenter = LanguageCommenters.INSTANCE.forLanguage(TypeSpecLanguage.INSTANCE)
        assertNotNull("No Commenter registered for TypeSpecLanguage in plugin.xml", commenter)
        assertTrue(
            "Expected a TypeSpecCommenter but got ${commenter?.javaClass}",
            commenter is TypeSpecCommenter,
        )
    }

    // ---- the Commenter interface's own return values -----------------------------

    fun testCommenterOwnReturnValues() {
        val commenter = TypeSpecCommenter()
        assertEquals("//", commenter.lineCommentPrefix)
        assertEquals("/*", commenter.blockCommentPrefix)
        assertEquals("*/", commenter.blockCommentSuffix)
        assertNull(commenter.commentedBlockCommentPrefix)
        assertNull(commenter.commentedBlockCommentSuffix)
    }

    // ---- line comment / uncomment --------------------------------------------------

    private fun doLineComment(before: String, after: String) {
        myFixture.configureByText("a.tsp", before)
        myFixture.performEditorAction(IdeActions.ACTION_COMMENT_LINE)
        myFixture.checkResult(after)
    }

    fun testCommentLineAtCaret() {
        doLineComment(
            "model <caret>Widget {}",
            "//model <caret>Widget {}",
        )
    }

    fun testUncommentAlreadyCommentedLine() {
        // Toggling ACTION_COMMENT_LINE again on an already-commented line removes the "//"
        // prefix only — TypeSpecCommenter's default LINE_COMMENT_ADD_SPACE is off, so no
        // separating space was inserted on comment and none is stripped on uncomment.
        doLineComment(
            "// model <caret>Widget {}",
            " model <caret>Widget {}",
        )
    }

    fun testCommentLineOnLineAlreadyStartingWithSlashSlashNoSpace() {
        // A line that already starts with "//" (no leading whitespace before the marker,
        // no space after it) must still round-trip through toggle comment/uncomment.
        doLineComment(
            "//<caret>model Widget {}",
            "<caret>model Widget {}",
        )
    }

    fun testCommentMultiLineSelection() {
        val before = """
            <selection>model Widget {
            model Gadget {
            }</selection>
        """.trimIndent()
        myFixture.configureByText("a.tsp", before)
        myFixture.performEditorAction(IdeActions.ACTION_COMMENT_LINE)
        val result = myFixture.editor.document.text
        // Every line touched by the selection gets a "//" prefix. TypeSpecCommenter's
        // default LINE_COMMENT_ADD_SPACE is off, so no separating space is inserted.
        assertEquals(
            listOf("//model Widget {", "//model Gadget {", "//}"),
            result.lines(),
        )
    }

    // ---- block comment / uncomment -------------------------------------------------

    private fun doBlockComment(before: String, after: String) {
        myFixture.configureByText("a.tsp", before)
        myFixture.performEditorAction(IdeActions.ACTION_COMMENT_BLOCK)
        myFixture.checkResult(after)
    }

    fun testCommentBlockOnSelection() {
        val before = "<selection>model Widget {}</selection>"
        myFixture.configureByText("a.tsp", before)
        myFixture.performEditorAction(IdeActions.ACTION_COMMENT_BLOCK)
        // A selection spanning the whole (single) line puts the "/*"/"*/" markers on their
        // own lines, per the stock CommentByBlockCommentHandler whole-line-selection behaviour.
        assertEquals("/*\nmodel Widget {}*/\n", myFixture.editor.document.text)
    }

    fun testUncommentBlockOnAlreadyCommentedSelection() {
        val before = "<selection>/*model Widget {}*/</selection>"
        myFixture.configureByText("a.tsp", before)
        myFixture.performEditorAction(IdeActions.ACTION_COMMENT_BLOCK)
        assertEquals("model Widget {}", myFixture.editor.document.text)
    }
}
