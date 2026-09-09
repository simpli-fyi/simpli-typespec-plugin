package simpli.fyi.plugins.typespec.refactoring

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.IncorrectOperationException
import simpli.fyi.plugins.typespec.psi.TypeSpecNamespaceStatement
import java.io.File

/**
 * Plan 07 M6.5i acceptance (amendment 4), `docs/plans/07-rename.md` -- "M6.5i -- Namespace
 * rename, across every reopened declaration". Fixtures live under
 * `src/test/testData/rename/namespace/`. Cases 6 and 7 (the two that need a real disk and a real
 * symlink) are in [TypeSpecNamespaceRenameOnDiskTest] instead, per the plan's deviation note --
 * everything else stays light, since the defect class plan 08 closes is disk/VFS divergence, not
 * multi-file text outcome.
 */
class TypeSpecNamespaceRenameTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    private fun textOf(relativePath: String): String = File(testDataPath, relativePath).readText()

    private fun copyIn(relativePath: String) =
        myFixture.copyFileToProject("rename/namespace/$relativePath", relativePath.substringAfterLast('/'))

    // ---- acceptance 1 + 9: three reopened declarations + a `using`, blast radius visible --

    fun testThreeReopenedDeclarations_andUsing_allRewritten_blastRadiusVisible() {
        myFixture.configureFromExistingVirtualFile(copyIn("SharedA.tsp"))
        myFixture.copyFileToProject("rename/namespace/SharedB.tsp", "SharedB.tsp")
        myFixture.copyFileToProject("rename/namespace/SharedC.tsp", "SharedC.tsp")
        myFixture.copyFileToProject("rename/namespace/Consumer.tsp", "Consumer.tsp")

        val namespace = PsiTreeUtil.findChildOfType(myFixture.file, TypeSpecNamespaceStatement::class.java)!!

        // "Blast radius is visible" (owner ratification 4): the sibling set `prepareRenaming`
        // adds must be observable directly, not inferred after the fact.
        val allRenames = mutableMapOf<com.intellij.psi.PsiElement, String>()
        TypeSpecRenamePsiElementProcessor().prepareRenaming(namespace, "Renamed", allRenames)
        assertEquals(
            "expected exactly the two OTHER reopenings (SharedB, SharedC) to be added to " +
                "allRenames -- the caret's own declaration (SharedA) and the `using` reference " +
                "are not declarations and are handled elsewhere in the rename pipeline",
            2,
            allRenames.size,
        )

        myFixture.renameElement(namespace, "Renamed")

        val psiManager = PsiManager.getInstance(project)
        fun psiFor(path: String) = psiManager.findFile(myFixture.findFileInTempDir(path)!!)!!

        assertEquals(textOf("rename/namespace/SharedA_after.tsp"), psiFor("SharedA.tsp").text)
        assertEquals(textOf("rename/namespace/SharedB_after.tsp"), psiFor("SharedB.tsp").text)
        assertEquals(textOf("rename/namespace/SharedC_after.tsp"), psiFor("SharedC.tsp").text)
        assertEquals(textOf("rename/namespace/Consumer_after.tsp"), psiFor("Consumer.tsp").text)
    }

    // ---- acceptance 2: qualified `Shared.Alpha` usage, only `Shared` changes -------------

    fun testQualifiedUsage_onlySharedSegmentRewritten() {
        myFixture.configureFromExistingVirtualFile(copyIn("SharedA.tsp"))
        myFixture.copyFileToProject("rename/namespace/Qualified.tsp", "Qualified.tsp")

        val namespace = PsiTreeUtil.findChildOfType(myFixture.file, TypeSpecNamespaceStatement::class.java)!!
        myFixture.renameElement(namespace, "Renamed")

        val qualifiedPsi = PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("Qualified.tsp")!!)!!
        assertEquals(textOf("rename/namespace/Qualified_after.tsp"), qualifiedPsi.text)
    }

    // ---- acceptance 3: nested block form and dotted reopening are the same namespace ------

    fun testNestedBlockAndDottedReopening_recognisedAsSameNamespace_bothRewritten() {
        myFixture.configureFromExistingVirtualFile(copyIn("NestedOuterInner.tsp"))
        myFixture.copyFileToProject("rename/namespace/DottedReopen.tsp", "DottedReopen.tsp")

        val innerNamespace = PsiTreeUtil.findChildrenOfType(myFixture.file, TypeSpecNamespaceStatement::class.java)
            .first { it.name == "Inner" }
        myFixture.renameElement(innerNamespace, "Renamed")

        assertEquals(textOf("rename/namespace/NestedOuterInner_after.tsp"), myFixture.file.text)
        val dottedPsi = PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("DottedReopen.tsp")!!)!!
        assertEquals(textOf("rename/namespace/DottedReopen_after.tsp"), dottedPsi.text)
    }

    // ---- acceptance 4: dotted namespace A.B.C, all four invocation paths (ADR 0012 F7) ----

    private fun configureDottedFixture(): com.intellij.openapi.vfs.VirtualFile {
        val dotted = copyIn("Dotted.tsp")
        myFixture.copyFileToProject("rename/namespace/DottedUser.tsp", "DottedUser.tsp")
        return dotted
    }

    fun test4a_caretOnC_rewritesFinalSegmentEverywhere() {
        myFixture.configureFromExistingVirtualFile(configureDottedFixture())
        val offset = myFixture.file.text.indexOf("A.B.C;") + "A.B.".length
        myFixture.editor.caretModel.moveToOffset(offset)
        myFixture.renameElementAtCaretUsingHandler("Z")

        assertEquals(textOf("rename/namespace/Dotted_afterZ.tsp"), myFixture.file.text)
        val userPsi = PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("DottedUser.tsp")!!)!!
        assertEquals(textOf("rename/namespace/DottedUser_afterZ.tsp"), userPsi.text)
    }

    fun test4b_caretOnB_refused_everythingByteIdentical() {
        myFixture.configureFromExistingVirtualFile(configureDottedFixture())
        val dottedBefore = myFixture.file.text
        val userVf = myFixture.findFileInTempDir("DottedUser.tsp")!!
        val userBefore = PsiManager.getInstance(project).findFile(userVf)!!.text

        val offset = myFixture.file.text.indexOf("A.B.C;") + "A.".length
        myFixture.editor.caretModel.moveToOffset(offset)
        var thrown: Throwable? = null
        try {
            myFixture.renameElementAtCaretUsingHandler("Z")
        } catch (t: Throwable) {
            thrown = t
        }

        assertEquals(
            "caret on B must be refused by the platform (ADR 0012 D6/F7) -- if this test's " +
                "assertion of no change fails, the finding was wrong and this milestone must " +
                "stop, per the plan's risk note; do not weaken this test",
            dottedBefore,
            myFixture.file.text,
        )
        assertEquals(
            "the user file must also be byte-identical -- a caret-on-B rename that silently " +
                "touched anything would be exactly the mid-segment misfire ADR 0012 refutes",
            userBefore,
            PsiManager.getInstance(project).findFile(userVf)!!.text,
        )
        // Recorded for the report, not asserted on: whether the platform threw or silently
        // declined determines which failure mode was observed.
        @Suppress("UNUSED_EXPRESSION")
        thrown
    }

    fun test4c_caretOnA_refused_everythingByteIdentical() {
        myFixture.configureFromExistingVirtualFile(configureDottedFixture())
        val dottedBefore = myFixture.file.text
        val userVf = myFixture.findFileInTempDir("DottedUser.tsp")!!
        val userBefore = PsiManager.getInstance(project).findFile(userVf)!!.text

        val offset = myFixture.file.text.indexOf("A.B.C;")
        myFixture.editor.caretModel.moveToOffset(offset)
        try {
            myFixture.renameElementAtCaretUsingHandler("Z")
        } catch (_: Throwable) {
            // refusal expected; see test4b for the same caveat
        }

        assertEquals("caret on A must be refused", dottedBefore, myFixture.file.text)
        assertEquals(
            "the user file must also be byte-identical",
            userBefore,
            PsiManager.getInstance(project).findFile(userVf)!!.text,
        )
    }

    fun test4d_viewInvoked_noCaret_rewritesFinalSegment_sameAsCaretOnC() {
        myFixture.configureFromExistingVirtualFile(configureDottedFixture())
        val namespace = PsiTreeUtil.findChildOfType(myFixture.file, TypeSpecNamespaceStatement::class.java)!!
        // No caret is moved at all -- this is the Project/Structure-view invocation path.
        myFixture.renameElement(namespace, "Z")

        assertEquals(textOf("rename/namespace/Dotted_afterZ.tsp"), myFixture.file.text)
        val userPsi = PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("DottedUser.tsp")!!)!!
        assertEquals(textOf("rename/namespace/DottedUser_afterZ.tsp"), userPsi.text)
    }

    // ---- acceptance 5: the wrong-segment hazard AND the split-namespace conflict report ---

    fun test5_wrongSegmentHazard_notRewritten_andReportedAsConflict() {
        myFixture.configureFromExistingVirtualFile(copyIn("WrongSegmentA.tsp"))
        myFixture.copyFileToProject("rename/namespace/WrongSegmentB.tsp", "WrongSegmentB.tsp")

        val bVf = myFixture.findFileInTempDir("WrongSegmentB.tsp")!!
        val bBefore = PsiManager.getInstance(project).findFile(bVf)!!.text

        val namespace = PsiTreeUtil.findChildOfType(myFixture.file, TypeSpecNamespaceStatement::class.java)!!
        assertEquals("Shared", namespace.name)

        // NOTE: `org.junit.Assert.assertThrows` is shadowed here by the inherited, void-returning
        // `com.intellij.testFramework.UsefulTestCase.assertThrows` -- Kotlin resolves the
        // unqualified call to the member, not the import, so the exception is caught by hand
        // instead in order to inspect it.
        var thrown: Throwable? = null
        try {
            myFixture.renameElement(namespace, "Renamed")
        } catch (t: Throwable) {
            thrown = t
        }
        assertTrue(
            "expected BaseRefactoringProcessor.ConflictsInTestsException, got $thrown",
            thrown is BaseRefactoringProcessor.ConflictsInTestsException,
        )
        val conflicts = thrown as BaseRefactoringProcessor.ConflictsInTestsException

        // a. B.tsp -- the "Sub" reopening -- must be completely untouched.
        assertEquals(
            "namespace Shared.Sub; in WrongSegmentB.tsp must NOT be rewritten -- this is the " +
                "wrong-edit guard for approach 3's filter",
            bBefore,
            PsiManager.getInstance(project).findFile(bVf)!!.text,
        )

        // b. the conflict must name the file left behind. `getMessages()` is not visible on the
        // compile-time API jar this project builds against, so this goes through the inherited
        // `Throwable.getMessage()` instead, which `BaseRefactoringProcessor.ConflictsInTestsException`
        // overrides to join its messages.
        val message = conflicts.message
        assertTrue(
            "expected a conflict message naming WrongSegmentB.tsp, got: $message",
            message?.contains("WrongSegmentB.tsp") == true,
        )
    }

    // ---- acceptance 8: dumb mode, both doors -----------------------------------------------

    fun test8_dumbMode_handlerDoor_refusesOrDeclinesWithoutRewriting() {
        myFixture.configureFromExistingVirtualFile(copyIn("SharedA.tsp"))
        myFixture.copyFileToProject("rename/namespace/SharedB.tsp", "SharedB.tsp")
        val before = myFixture.file.text
        val bVf = myFixture.findFileInTempDir("SharedB.tsp")!!
        val bBefore = PsiManager.getInstance(project).findFile(bVf)!!.text

        DumbModeTestUtils.runInDumbModeSynchronously(project) {
            val namespace = PsiTreeUtil.findChildOfType(myFixture.file, TypeSpecNamespaceStatement::class.java)!!
            val dataContext = SimpleDataContext.builder()
                .add(CommonDataKeys.PROJECT, project)
                .add(CommonDataKeys.PSI_ELEMENT, namespace)
                .build()
            myFixture.editor.caretModel.moveToOffset(myFixture.file.text.indexOf("Shared;"))
            try {
                myFixture.renameElementAtCaretUsingHandler("Renamed")
            } catch (_: Throwable) {
                // refusal via the handler door is an acceptable outcome; the file must not
                // change either way -- checked unconditionally below.
            }
            @Suppress("UNUSED_EXPRESSION")
            dataContext
        }

        assertEquals(
            "the handler path must not rewrite SharedA.tsp while the project is dumb",
            before,
            myFixture.file.text,
        )
        assertEquals(
            "the handler path must not rewrite SharedB.tsp while the project is dumb -- a " +
                "partial rewrite here (A changed, B left behind) is the exact split-namespace " +
                "hazard this milestone exists to prevent",
            bBefore,
            PsiManager.getInstance(project).findFile(bVf)!!.text,
        )
    }

    fun test8_dumbMode_directProcessorDoor_throwsIncorrectOperationException() {
        myFixture.configureFromExistingVirtualFile(copyIn("SharedA.tsp"))
        myFixture.copyFileToProject("rename/namespace/SharedB.tsp", "SharedB.tsp")
        val before = myFixture.file.text
        val bVf = myFixture.findFileInTempDir("SharedB.tsp")!!
        val bBefore = PsiManager.getInstance(project).findFile(bVf)!!.text

        var thrown: Throwable? = null
        DumbModeTestUtils.runInDumbModeSynchronously(project) {
            val namespace = PsiTreeUtil.findChildOfType(myFixture.file, TypeSpecNamespaceStatement::class.java)!!
            val allRenames = mutableMapOf<com.intellij.psi.PsiElement, String>()
            try {
                TypeSpecRenamePsiElementProcessor().prepareRenaming(namespace, "Renamed", allRenames)
            } catch (t: Throwable) {
                thrown = t
            }
        }

        assertNotNull(
            "prepareRenaming must throw while the project is dumb (M6.5i approach 6) -- " +
                "returning an empty sibling set instead would make RenameProcessor rename only " +
                "the caret's declaration and silently split the namespace",
            thrown,
        )
        assertTrue(
            "expected IncorrectOperationException, got ${thrown?.javaClass}",
            thrown is IncorrectOperationException,
        )
        assertEquals(
            "a partial write must not have happened before the throw (SharedA.tsp)",
            before,
            myFixture.file.text,
        )
        assertEquals(
            "a partial write must not have happened before the throw (SharedB.tsp)",
            bBefore,
            PsiManager.getInstance(project).findFile(bVf)!!.text,
        )
    }
}
