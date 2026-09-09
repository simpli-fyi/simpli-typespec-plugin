package simpli.fyi.plugins.typespec.refactoring

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.io.IoTestUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.IndexingTestUtil
import simpli.fyi.plugins.typespec.fixtures.TypeSpecHeavyFixtureTestCase
import simpli.fyi.plugins.typespec.fixtures.TypeSpecWorkspaceLayout
import simpli.fyi.plugins.typespec.psi.TypeSpecModelStatement
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Plan 09 M6.5n -- branch coverage for [TypeSpecAliasedDocuments.reconcileBeforeWrite] (ADR
 * 0015, M6.5m) that [TypeSpecInplaceRenameOnDiskTest]'s two pins do not exercise on their own:
 * each of D1/D2's branches gets its own small, deliberate fixture instead of being inferred from
 * the 178-usage reproduction. Per plan 09's own risk note, these stay small (one dirty document,
 * not 178) -- the owner's scale belongs to `TypeSpecInplaceRenameOnDiskTest` alone.
 *
 * All symlink-dependent cases start with [IoTestUtil.assumeSymLinkCreationIsSupported] (via
 * [TypeSpecWorkspaceLayout.npmWorkspace] or this class's own [buildNestedNodeModulesSymlink]) and
 * are skipped, not failed, on a platform that cannot create them. On this run's CI/dev host
 * (macOS/Darwin) symlink creation is supported, so every case below actually ran -- none were
 * skipped.
 */
class TypeSpecAliasedDocumentsTest : TypeSpecHeavyFixtureTestCase() {

    private fun primaryFoo(layout: TypeSpecWorkspaceLayout.Layout): TypeSpecModelStatement =
        PsiTreeUtil.findChildrenOfType(layout.main.psiFile, TypeSpecModelStatement::class.java)
            .first { it.name == "Foo" }

    private fun dirty(virtualFile: com.intellij.openapi.vfs.VirtualFile, marker: String) {
        fixture.openFileInEditor(virtualFile)
        val document = requireNotNull(FileDocumentManager.getInstance().getDocument(virtualFile))
        WriteCommandAction.runWriteCommandAction(project) {
            document.insertString(document.textLength, marker)
        }
    }

    /**
     * Runs just the handler call (no [settleVfs]) and returns what it threw, or `null`. Split
     * out from [renameFooToBar] so a case that needs to inspect document-dirty state right after
     * the rename -- before [settleVfs]'s own `saveAllDocuments()` unconditionally clears it for
     * *every* open document, ignored ones included -- can do so without that call masking the
     * very thing being asserted (cases 4 and 5).
     */
    private fun renameFooToBarHandlerOnly(layout: TypeSpecWorkspaceLayout.Layout): Throwable? {
        fixture.configureFromExistingVirtualFile(layout.main.real)
        fixture.editor.caretModel.moveToOffset(fixture.file.text.indexOf("Foo") + 1)
        fixture.editor.settings.isVariableInplaceRenameEnabled = false
        return try {
            fixture.renameElementAtCaretUsingHandler("Bar")
            null
        } catch (t: Throwable) {
            t
        }
    }

    private fun settleOnly(layout: TypeSpecWorkspaceLayout.Layout): Throwable? = try {
        settleVfs(layout.tempRoot)
        null
    } catch (t: Throwable) {
        t
    }

    private fun renameFooToBar(layout: TypeSpecWorkspaceLayout.Layout): Throwable? =
        renameFooToBarHandlerOnly(layout) ?: settleOnly(layout)

    // ---- Case 1 -- dirty twin of an unrelated in-scope file: saved, rename proceeds -------

    fun testCase1_dirtyTwinOfUnrelatedFile_isSaved_renameProceeds() {
        val layout = TypeSpecWorkspaceLayout.npmWorkspace(fixture)
        val marker = "\n// dirtied via node_modules twin\n"
        dirty(layout.user.nodeModulesTwin, marker)
        val twinDocument = requireNotNull(FileDocumentManager.getInstance().getDocument(layout.user.nodeModulesTwin))
        assertTrue("twin document must start dirty", FileDocumentManager.getInstance().isDocumentUnsaved(twinDocument))

        val thrown = renameFooToBar(layout)
        assertNull("an unrelated file's dirty twin must not block the rename: $thrown", thrown)

        assertFalse(
            "the twin's document must have been saved by the reconciler before the write",
            FileDocumentManager.getInstance().isDocumentUnsaved(twinDocument),
        )
        val realUserPath = Paths.get(fixture.tempDirPath, "model", "pkg", "User.tsp")
        val twinUserPath = Paths.get(fixture.tempDirPath, "node_modules", "pkg", "User.tsp")
        val realUserText = Files.readString(realUserPath)
        assertTrue(
            "the twin's unsaved marker must have reached disk: $realUserText",
            realUserText.contains("dirtied via node_modules twin"),
        )
        assertEquals(
            "exactly one Bar after the rename is applied on top of the saved marker",
            1,
            Regex("\\bBar\\b").findAll(realUserText).count(),
        )
        assertEquals(
            "zero Foo left after the rename",
            0,
            Regex("\\bFoo\\b").findAll(realUserText).count(),
        )
        assertEquals(
            "twin and real are the same inode -- must read back identical content",
            realUserText,
            Files.readString(twinUserPath),
        )
    }

    // ---- Case 2 -- twin AND canonical both dirty: refused, nothing half-applied -----------

    fun testCase2_twinAndCanonicalBothDirty_refused_noPartialWrite() {
        val layout = TypeSpecWorkspaceLayout.npmWorkspace(fixture)
        val mainBefore = Files.readString(layout.main.real.toNioPath())
        val userBefore = Files.readString(layout.user.real.toNioPath())

        dirty(layout.user.real, "\n// dirtied via real path\n")
        dirty(layout.user.nodeModulesTwin, "\n// dirtied via node_modules twin\n")
        val realDocument = requireNotNull(FileDocumentManager.getInstance().getDocument(layout.user.real))
        val twinDocument = requireNotNull(FileDocumentManager.getInstance().getDocument(layout.user.nodeModulesTwin))

        val thrown = renameFooToBar(layout)

        assertNotNull("two dirty documents over one inode must refuse the rename", thrown)
        assertTrue(
            "expected IncorrectOperationException, got ${thrown?.javaClass}: ${thrown?.message}",
            thrown is com.intellij.util.IncorrectOperationException,
        )
        val message = requireNotNull(thrown?.message)
        assertTrue("message must name the real path: $message", message.contains(layout.user.real.path))
        assertTrue("message must name the twin path: $message", message.contains(layout.user.nodeModulesTwin.path))

        assertTrue(
            "the real document must still be unsaved -- the refusal must not half-apply",
            FileDocumentManager.getInstance().isDocumentUnsaved(realDocument),
        )
        assertTrue(
            "the twin document must still be unsaved -- the refusal must not half-apply",
            FileDocumentManager.getInstance().isDocumentUnsaved(twinDocument),
        )
        assertEquals(
            "Main.tsp must be byte-identical on disk after the refusal",
            mainBefore,
            Files.readString(layout.main.real.toNioPath()),
        )
        assertEquals(
            "User.tsp must be byte-identical on disk after the refusal",
            userBefore,
            Files.readString(layout.user.real.toNioPath()),
        )
    }

    // ---- Case 3 -- twin of the file containing the renamed element: refused ---------------

    fun testCase3_twinOfContainingFile_refused_declarationUnchanged() {
        val layout = TypeSpecWorkspaceLayout.npmWorkspace(fixture)
        val mainBefore = Files.readString(layout.main.real.toNioPath())

        dirty(layout.main.nodeModulesTwin, "\n// dirtied via node_modules twin\n")
        val twinDocument = requireNotNull(FileDocumentManager.getInstance().getDocument(layout.main.nodeModulesTwin))

        val thrown = renameFooToBar(layout)

        assertNotNull("a dirty twin of the declaration's own file must refuse the rename", thrown)
        assertTrue(
            "expected IncorrectOperationException, got ${thrown?.javaClass}: ${thrown?.message}",
            thrown is com.intellij.util.IncorrectOperationException,
        )
        val message = requireNotNull(thrown?.message)
        assertTrue("message must name the real path: $message", message.contains(layout.main.real.path))
        assertTrue("message must name the twin path: $message", message.contains(layout.main.nodeModulesTwin.path))

        assertTrue(
            "the twin document must still be unsaved after the refusal",
            FileDocumentManager.getInstance().isDocumentUnsaved(twinDocument),
        )
        assertEquals(
            "Main.tsp must be byte-identical on disk after the refusal",
            mainBefore,
            Files.readString(layout.main.real.toNioPath()),
        )
        val mainPsi = requireNotNull(PsiManager.getInstance(project).findFile(layout.main.real))
        assertTrue(
            "the declaration must still read Foo after the refused rename: ${mainPsi.text}",
            mainPsi.text.contains("Foo"),
        )
    }

    // ---- Case 4 -- dirty buffer on a non-.tsp file: ignored, rename proceeds --------------

    fun testCase4_dirtyNonTspFile_isIgnored_renameProceeds() {
        val layout = TypeSpecWorkspaceLayout.npmWorkspace(fixture)
        val notesFile = fixture.addFileToProject("notes.txt", "hello\n").virtualFile
        val notesBefore = Files.readString(notesFile.toNioPath())
        dirty(notesFile, "\n// unsaved note\n")
        val notesDocument = requireNotNull(FileDocumentManager.getInstance().getDocument(notesFile))
        assertTrue(
            "the non-.tsp document must start dirty",
            FileDocumentManager.getInstance().isDocumentUnsaved(notesDocument),
        )

        val handlerThrown = renameFooToBarHandlerOnly(layout)
        assertNull("a dirty non-.tsp buffer must not block the rename: $handlerThrown", handlerThrown)

        // Checked BEFORE settleVfs -- its own saveAllDocuments() would save every open document,
        // ignored ones included, and mask the very thing this case asserts.
        assertTrue(
            "a non-.tsp document must be ignored entirely by the reconciler -- still unsaved",
            FileDocumentManager.getInstance().isDocumentUnsaved(notesDocument),
        )
        assertEquals(
            "a non-.tsp document must never be saved by the reconciler",
            notesBefore,
            Files.readString(notesFile.toNioPath()),
        )

        val settleThrown = settleOnly(layout)
        assertNull("settling afterwards must not throw either: $settleThrown", settleThrown)
    }

    // ---- Case 5 -- canonical file outside tspScope (nested inside node_modules): ignored --

    /**
     * Not the workspace-symlink shape ([TypeSpecWorkspaceLayout] -- `node_modules/pkg` pointing
     * *out* to real project source): both ends of this symlink live *inside* `node_modules`
     * (the pnpm/nested-node_modules shape), so `canonicalFile` still resolves to a path
     * [simpli.fyi.plugins.typespec.resolve.TypeSpecSearchScopes.tspScope] excludes. This
     * exercises D1's scope-exclusion branch specifically, not merely the "not an alias" one.
     */
    fun testCase5_canonicalOutsideTspScope_realLibraryFileUnderNodeModules_isIgnored() {
        IoTestUtil.assumeSymLinkCreationIsSupported()
        val layout = TypeSpecWorkspaceLayout.npmWorkspace(fixture)

        val tempDirPath = fixture.tempDirPath
        Files.createDirectories(Paths.get(tempDirPath, "node_modules", "libA"))
        fixture.addFileToProject("node_modules/libA/Real.tsp", "model LibModel {}\n")
        IoTestUtil.createSymLink(
            "$tempDirPath/node_modules/libA",
            "$tempDirPath/node_modules/libA-twin",
        )
        val tempRoot = requireNotNull(fixture.tempDirFixture.getFile("."))
        VfsUtil.markDirtyAndRefresh(false, true, true, tempRoot)
        IndexingTestUtil.waitUntilIndexesAreReady(project)

        val realLib = requireNotNull(
            LocalFileSystem.getInstance().refreshAndFindFileByPath("$tempDirPath/node_modules/libA/Real.tsp"),
        )
        val twinLib = requireNotNull(
            LocalFileSystem.getInstance().refreshAndFindFileByPath("$tempDirPath/node_modules/libA-twin/Real.tsp"),
        )
        assertEquals(
            "the nested node_modules alias must still canonicalise to the real library file",
            realLib.canonicalFile,
            twinLib.canonicalFile,
        )

        val libBefore = Files.readString(realLib.toNioPath())
        dirty(twinLib, "\n// dirtied via nested node_modules alias\n")
        val libDocument = requireNotNull(FileDocumentManager.getInstance().getDocument(twinLib))
        assertTrue(
            "the nested library twin's document must start dirty",
            FileDocumentManager.getInstance().isDocumentUnsaved(libDocument),
        )

        val handlerThrown = renameFooToBarHandlerOnly(layout)
        assertNull(
            "a dirty buffer whose canonical file lies outside tspScope must not block the rename: $handlerThrown",
            handlerThrown,
        )

        // Checked BEFORE settleVfs -- see testCase4's comment for why.
        assertTrue(
            "a document whose canonical file lies outside tspScope must be ignored -- still unsaved",
            FileDocumentManager.getInstance().isDocumentUnsaved(libDocument),
        )
        assertEquals(
            "the real library file must never be saved by the reconciler",
            libBefore,
            Files.readString(realLib.toNioPath()),
        )

        val settleThrown = settleOnly(layout)
        assertNull("settling afterwards must not throw either: $settleThrown", settleThrown)
    }

    // ---- Case 6 -- no unsaved documents at all: no-op, rename proceeds --------------------

    fun testCase6_noUnsavedDocuments_reconcilerIsNoOp_renameProceeds() {
        val layout = TypeSpecWorkspaceLayout.npmWorkspace(fixture)
        assertTrue(
            "precondition: no unsaved documents before the rename",
            FileDocumentManager.getInstance().unsavedDocuments.isEmpty(),
        )

        TypeSpecAliasedDocuments.reconcileBeforeWrite(project, primaryFoo(layout), emptyMap())
        assertTrue(
            "reconcileBeforeWrite must not create or touch any unsaved document when there are " +
                "none to begin with",
            FileDocumentManager.getInstance().unsavedDocuments.isEmpty(),
        )

        val thrown = renameFooToBar(layout)
        assertNull("the rename must still proceed normally with nothing to reconcile: $thrown", thrown)

        val realUserText = Files.readString(layout.user.real.toNioPath())
        assertEquals(
            "exactly one Bar after the rename",
            1,
            Regex("\\bBar\\b").findAll(realUserText).count(),
        )
    }
}
