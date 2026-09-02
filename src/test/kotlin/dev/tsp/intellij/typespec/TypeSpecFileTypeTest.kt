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
     * KNOWN DEFECT — see the M2 test report. `FileTypeManager` correctly resolves `.tsp` to
     * [TypeSpecFileType] (see the two tests above), but a real PSI file created for a `.tsp`
     * path in this test fixture (via `myFixture.configureByText`/`addFileToProject`) comes
     * back as a plain `com.intellij.openapi.fileTypes.PlainTextFileType` / `PsiPlainTextFileImpl`,
     * not [TypeSpecFileType] / [TypeSpecLanguage]. Root cause, confirmed by direct debugging:
     * the platform appears to require a registered `lang.parserDefinition` for a
     * `LanguageFileType` before it will actually build a PSI file of that type; TypeSpec has
     * none until M5. This is left failing deliberately — do not weaken it — because it is
     * exactly the platform behavior M1's acceptance criteria (plan 01, "Acceptance") assumed
     * would already work.
     */
    fun testConfiguredFileHasTypeSpecLanguageAndFileType() {
        myFixture.configureByText("demo.tsp", "namespace Demo;")
        assertEquals(TypeSpecLanguage.INSTANCE, myFixture.file.language)
        assertEquals(TypeSpecFileType.INSTANCE, myFixture.file.fileType)
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
