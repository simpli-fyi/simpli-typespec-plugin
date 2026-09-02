package simpli.fyi.plugins.typespec

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
     * Corrected per ADR 0005 D6 (amending ADR 0003 F1's now-stale note): `.tsp` files
     * previously fell back to `PsiPlainTextFileImpl` because no `ParserDefinition` was
     * registered. `TypeSpecParserDefinition` (ADR 0005 M4b) now supplies a real
     * `TypeSpecFile` PSI, so `psiFile.language` is `TypeSpecLanguage` and `psiFile.fileType`
     * is `TypeSpecFileType` — no plain-text fallback remains to document.
     */
    fun testConfiguredFileHasTypeSpecLanguageAndFileType() {
        val psiFile = myFixture.configureByText("demo.tsp", "namespace Demo;")
        assertEquals(TypeSpecFileType.INSTANCE, psiFile.virtualFile.fileType)
        assertEquals(TypeSpecLanguage.INSTANCE, psiFile.viewProvider.baseLanguage)
        assertEquals(TypeSpecLanguage.INSTANCE, psiFile.language)
        assertEquals(TypeSpecFileType.INSTANCE, psiFile.fileType)
        assertTrue(
            "Expected a TypeSpecFile but got ${psiFile.javaClass}",
            psiFile is simpli.fyi.plugins.typespec.psi.TypeSpecFile,
        )
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
