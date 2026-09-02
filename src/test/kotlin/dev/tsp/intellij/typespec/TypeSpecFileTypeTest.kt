package dev.tsp.intellij.typespec

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * M1 acceptance: `.tsp` is registered as [TypeSpecFileType] / [TypeSpecLanguage].
 */
class TypeSpecFileTypeTest : BasePlatformTestCase() {

    fun testExtensionMapsToTypeSpecFileType() {
        assertSame(
            TypeSpecFileType.INSTANCE,
            FileTypeManager.getInstance().getFileTypeByExtension("tsp"),
        )
    }

    fun testFileNameMapsToTypeSpecFileType() {
        assertSame(
            TypeSpecFileType.INSTANCE,
            FileTypeManager.getInstance().getFileTypeByFileName("x.tsp"),
        )
    }

    /**
     * Expected platform behaviour, confirmed against ideaIC-2025.2.6.3 /
     * intellij-community source: `AbstractFileViewProvider.createFile(Language)`
     * (AbstractFileViewProvider.java:157-163) returns `null` when no `ParserDefinition`
     * is registered for the language, so the caller falls through to
     * `new PsiPlainTextFileImpl(this)` (:154). `PsiPlainTextFileImpl` (:22-24) then
     * force-overwrites `myFileType = PlainTextFileType.INSTANCE` precisely *because*
     * the base language is not plain text.
     *
     * TypeSpec has no `ParserDefinition` until M5, so `psiFile.language` legitimately
     * reports `Language.ANY`/plain text for now — that is not asserted here. What does
     * stay correct through this fallback, and is what M1's acceptance criteria actually
     * depends on, is the *virtual file's* file type and the *view provider's* base
     * language, both of which are set before `PsiPlainTextFileImpl` does its override.
     */
    fun testConfiguredFileHasTypeSpecLanguageAndFileType() {
        val psiFile = myFixture.configureByText("demo.tsp", "namespace Demo;")
        assertEquals(TypeSpecFileType.INSTANCE, psiFile.virtualFile.fileType)
        assertEquals(TypeSpecLanguage.INSTANCE, psiFile.viewProvider.baseLanguage)
    }

    fun testDefaultExtension() {
        assertEquals("tsp", TypeSpecFileType.INSTANCE.defaultExtension)
    }

    fun testIconLoads() {
        assertNotNull(TypeSpecFileType.INSTANCE.icon)
    }

    fun testLanguageDisplayName() {
        assertEquals("TypeSpec", TypeSpecLanguage.INSTANCE.displayName)
    }

    fun testFileTypeName() {
        assertEquals("TypeSpec", TypeSpecFileType.INSTANCE.name)
    }
}
