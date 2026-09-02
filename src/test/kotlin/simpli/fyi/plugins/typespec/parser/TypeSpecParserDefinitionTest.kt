package simpli.fyi.plugins.typespec.parser

import com.intellij.lang.LanguageCommenters
import com.intellij.psi.PsiComment
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import simpli.fyi.plugins.typespec.TypeSpecFileType
import simpli.fyi.plugins.typespec.TypeSpecLanguage
import simpli.fyi.plugins.typespec.editor.TypeSpecCommenter
import simpli.fyi.plugins.typespec.psi.TypeSpecFile
import java.io.File

/**
 * M4b acceptance (ADR 0005 D5): [TypeSpecParserDefinition] — the minimal flat
 * `ParserDefinition` that gives `.tsp` files a real file language instead of the
 * `PsiPlainTextFileImpl` fallback (ADR 0003 F1).
 */
class TypeSpecParserDefinitionTest : BasePlatformTestCase() {

    fun testLanguageAndFileTypeResolveToTypeSpec() {
        val psiFile = myFixture.configureByText("demo.tsp", "namespace Demo;")
        assertEquals(TypeSpecLanguage.INSTANCE, psiFile.language)
        assertEquals(TypeSpecFileType.INSTANCE, psiFile.fileType)
    }

    fun testPsiFileIsTypeSpecFileNotPlainText() {
        val psiFile = myFixture.configureByText("demo.tsp", "namespace Demo;")
        assertTrue(
            "Expected a TypeSpecFile but got ${psiFile.javaClass}",
            psiFile is TypeSpecFile,
        )
        assertFalse(
            "Must not fall back to PsiPlainTextFileImpl",
            psiFile.javaClass.name.contains("PlainText"),
        )
    }

    fun testTextRoundTripsLosslesslyForKitchenSink() {
        val source = File("src/test/testData/lexer/kitchen-sink.tsp").readText()
        val psiFile = myFixture.configureByText("kitchen-sink.tsp", source)
        assertEquals(source, psiFile.text)
    }

    fun testCommentsAreRealPsiCommentLeaves() {
        val source = """
            // line comment
            /* block comment */
            /** doc comment */
            model Widget {}
        """.trimIndent()
        val psiFile = myFixture.configureByText("comments.tsp", source)

        val comments = PsiTreeUtil.findChildrenOfType(psiFile, PsiComment::class.java)
        assertFalse("Expected at least one PsiComment leaf", comments.isEmpty())

        val commentTexts = comments.map { it.text }
        assertTrue(commentTexts.any { it == "// line comment" })
        assertTrue(commentTexts.any { it == "/* block comment */" })
        assertTrue(commentTexts.any { it == "/** doc comment */" })

        for (comment in comments) {
            val sourceSlice = source.substring(comment.textRange.startOffset, comment.textRange.endOffset)
            assertEquals(sourceSlice, comment.text)
        }
    }

    fun testLanguageCommentersResolvesTypeSpecCommenter() {
        val psiFile = myFixture.configureByText("demo.tsp", "namespace Demo;")
        val commenter = LanguageCommenters.INSTANCE.forLanguage(psiFile.language)
        assertNotNull("No Commenter resolved for psiFile.language", commenter)
        assertTrue(
            "Expected a TypeSpecCommenter but got ${commenter?.javaClass}",
            commenter is TypeSpecCommenter,
        )
    }
}
