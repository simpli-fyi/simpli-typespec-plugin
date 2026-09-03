package simpli.fyi.plugins.typespec.resolve

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import simpli.fyi.plugins.typespec.psi.TypeSpecFile

/**
 * Pins ADR 0004 D2's cache-dependency choice for [TypeSpecFileDeclarations] (plan 02
 * "Acceptance — what `tsp-tester` writes" § `TypeSpecCachingTest`): the cache dependency is
 * the file ITSELF, not `PsiModificationTracker.MODIFICATION_COUNT`. Without this test, a later
 * "cleanup" to `MODIFICATION_COUNT` passes every other test in the suite and quietly makes the
 * plugin unusable on a large project (every keystroke anywhere invalidates every file's table).
 */
class TypeSpecCachingTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    fun testSameFileReturnsSameCachedInstance() {
        val file = myFixture.configureByText("caching-a.tsp", "model Foo {}") as TypeSpecFile
        val first = TypeSpecFileDeclarations.of(file)
        val second = TypeSpecFileDeclarations.of(file)
        assertSame(first, second)
    }

    fun testEditingOtherFileDoesNotInvalidateThisFilesCache() {
        val fileA = myFixture.addFileToProject("caching-file-a.tsp", "model Foo {}") as TypeSpecFile
        val beforeA = TypeSpecFileDeclarations.of(fileA)

        val virtualFileB = myFixture.addFileToProject("caching-file-b.tsp", "model Bar {}").virtualFile
        myFixture.configureFromExistingVirtualFile(virtualFileB)
        myFixture.editor.caretModel.moveToOffset(myFixture.editor.document.textLength)
        myFixture.type(" model Baz {}")
        myFixture.doHighlighting() // force any pending PSI/document reconciliation

        val afterA = TypeSpecFileDeclarations.of(fileA)
        assertSame(
            "editing an unrelated file must not invalidate file A's declaration table" +
                " (ADR 0004 D2 — cache dependency is the file itself, not MODIFICATION_COUNT)",
            beforeA,
            afterA,
        )
    }

    fun testEditingThisFileInvalidatesItsOwnCacheAndReflectsTheEdit() {
        val virtualFileA = myFixture.addFileToProject("caching-self-a.tsp", "model Foo {}").virtualFile
        myFixture.configureFromExistingVirtualFile(virtualFileA)
        val fileA = myFixture.file as TypeSpecFile

        val before = TypeSpecFileDeclarations.of(fileA)
        assertTrue(before.containsName("Foo"))
        assertFalse(before.containsName("Quux"))

        myFixture.editor.caretModel.moveToOffset(myFixture.editor.document.textLength)
        myFixture.type(" model Quux {}")
        myFixture.doHighlighting()

        val after = TypeSpecFileDeclarations.of(fileA)
        assertNotSame(before, after)
        assertTrue(after.containsName("Quux"))
    }
}
