package simpli.fyi.plugins.typespec.refactoring

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.io.IoTestUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.rename.inplace.MemberInplaceRenameHandler
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestUtil
import simpli.fyi.plugins.typespec.fixtures.TypeSpecHeavyFixtureTestCase
import simpli.fyi.plugins.typespec.psi.TypeSpecModelStatement
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Owner-reported bug (2026-09-09, tracked under plan 08): a real npm-workspace monorepo
 * (ADR 0013 layout) in-place-renamed a model spread into 178 files and produced 14 "File Cache
 * Conflict" dialogs -- `MemoryDiskConflictResolver` entries with `oldFileStamp: 0`, the same
 * signature as the original ADR 0013 defect. The dialog path (M6.5k's opt-out, exercised by
 * [TypeSpecRenameOnDiskTest.testPin2_renameEndToEnd_singleWriteReachesDisk_noConflictException])
 * produces no conflicts for the same rename in the owner's session -- so the theory going in was
 * that the in-place path, specifically, still writes some usages through their `node_modules`
 * symlink twin. Plan 08's Pins 2 and 3 are the only tests that ever built the symlink fixture,
 * and both opted OUT of in-place (`isVariableInplaceRenameEnabled = false`) to keep testing the
 * dialog path -- so the in-place path had never actually been run against a symlink layout
 * before this test.
 *
 * This is deliberately a sibling of [TypeSpecRenameOnDiskTest], not an addition to it: it does
 * NOT reuse [simpli.fyi.plugins.typespec.fixtures.TypeSpecWorkspaceLayout.npmWorkspace] (fixed at
 * one declaration + one usage file) because the owner's report is explicit that a single-usage
 * layout may not reproduce -- the declaration is in one file and usages are spread across many.
 * The layout is instead built by hand here, the same recipe
 * [TypeSpecNamespaceRenameOnDiskTest.testCase6_reopenedNamespaceOnDisk_npmWorkspaceSymlink_allThreeRewritten_noConflict]
 * uses, scaled to the owner's exact numbers: 178 usage files, 14 with a cached document.
 *
 * **What it took to reproduce, and what did not work (reported in full per the brief):**
 * 1. 178 usage files + 14 opened in the editor **through the real `model/` path**, no edits:
 *    no exception, all 178 files correctly rewritten. [testA_manyUsages_openedViaRealPath_noConflict]
 * 2. Same, but the 14 opened editors point at the **`node_modules` twin** instead of the real
 *    path (plausible in practice -- ctrl-clicking an `import "pkg/User.tsp"` from another
 *    package resolves through `node_modules`, so the tab a developer actually has open is the
 *    twin, not `model/User.tsp`): still no exception, so a *clean* cached twin document is just
 *    silently reloaded on VFS refresh -- no conflict. [testB_manyUsages_openedViaTwinPath_noEdits_noConflict]
 * 3. Same as 2, but each of those 14 twin documents is left **dirty** (unsaved edit, never
 *    saved) before the rename runs -- mirroring an editor tab with pending, uncommitted changes.
 *    **This reproduces the exact failure signature**: `IllegalStateException: Unexpected
 *    memory-disk conflict ... please use FileDocumentManager#reloadFromDisk or avoid VFS
 *    refresh`, raised against the *real* `model/pkg/UserN.tsp` path, from inside
 *    `MemoryDiskConflictResolver`. [testC_inplace_dirtyTwinDocument_noConflict] -- ADR 0015
 *    (plan 09 M6.5m/n) fixed this; the test is now a regression pin asserting no conflict and a
 *    correctly-applied rename.
 * 4. The same setup (3) driven through the **dialog path** instead
 *    (`isVariableInplaceRenameEnabled = false`, `renameElementAtCaretUsingHandler`) reproduces
 *    the *identical* exception. [testD_dialogPath_sameDirtyTwinDocument_alsoFixed]
 *
 * **Attribution: neither of the two leads.** (3) is what actually reproduces the owner's
 * signature, and (4) shows it is not specific to `MemberInplaceRenamer` at all -- the dialog
 * path throws the same way given the same starting state. That rules out both leads, which were
 * specifically about `MemberInplaceRenamer`/`MyRenameProcessor` (the hard-coded `projectScope`
 * constructor, and the automatic `AutomaticRenamerFactory` registration) -- neither is on the
 * call path exercised by the dialog's own `RenameDialog` -> `RenameProcessor`. The trigger is
 * unconditional on *any* refactoring write path: a dirty, unsaved cached `Document` open on a
 * `node_modules` twin `VirtualFile` of a file the rename is about to rewrite through its real
 * path. `RenameProcessor`/`BaseRefactoringProcessor` does not save or reconcile documents on
 * *other* `VirtualFile`s that alias the same on-disk bytes before performing the write; when
 * [TypeSpecHeavyFixtureTestCase.settleVfs] later calls `saveAllDocuments()`, the twin's stale,
 * dirty buffer is flushed to disk-content that has already changed underneath it via the real
 * path, and `MemoryDiskConflictResolver` (correctly) refuses to silently pick a winner.
 *
 * This still leaves the field question genuinely open: the owner's report was that *only* the
 * in-place attempt raised dialogs, and a same-shape dialog-path rename afterwards was clean --
 * but per (4) above, that is very plausibly because by the time the owner tried the dialog path
 * a second time, the offending tabs' documents were no longer dirty (either the in-place
 * attempt's own failure already forced a reload, or the user had already dealt with the first
 * batch of File Cache Conflict dialogs). This test does not have access to that ordering, so it
 * cannot rule in-or-out whether the *first* attempt specifically needed to be in-place to
 * surface a dirty twin document that pre-existed the session -- only that, given a dirty twin
 * document, the crash itself is not in-place-specific.
 *
 * **ADR 0015 closing note.** [testC_inplace_dirtyTwinDocument_noConflict] and
 * [testD_dialogPath_sameDirtyTwinDocument_alsoFixed] were originally written as tripwires
 * asserting the defect above *did* reproduce (`assertNotNull`), so the defect stayed executed on
 * every run rather than rotting behind an `@Ignore`. Plan 09 M6.5n inverted both to `assertNull`
 * once `TypeSpecAliasedDocuments.reconcileBeforeWrite` (ADR 0015, M6.5m) landed: they are now
 * regression pins for "a dirty `node_modules` twin document no longer produces a
 * `MemoryDiskConflictResolver` conflict, on either rename path."
 */
class TypeSpecInplaceRenameOnDiskTest : TypeSpecHeavyFixtureTestCase() {

    private val usageFileCount = 178
    private val openedUsageFileCount = 14

    /** Everything the [TypeSpecNamespaceRenameOnDiskTest] recipe builds, scaled to N usage files. */
    private class ManyUsageLayout(
        val tempRoot: VirtualFile,
        val realMain: VirtualFile,
        val realUsers: List<VirtualFile>,
        val twinUsers: List<VirtualFile>,
        val tempDirPath: String,
    )

    private fun buildManyUsageLayout(): ManyUsageLayout {
        IoTestUtil.assumeSymLinkCreationIsSupported()

        // Real bytes: one declaration, `usageFileCount` usage files -- the owner's exact shape.
        fixture.addFileToProject("model/pkg/Main.tsp", "model Foo {}\n")
        (1..usageFileCount).forEach { i ->
            fixture.addFileToProject(
                "model/pkg/User$i.tsp",
                "import \"./Main.tsp\";\nmodel Uses$i { a: Foo; }\n",
            )
        }

        // The symlink: node_modules/pkg -> ../model/pkg (directory symlink), ADR 0013's shape --
        // every file above is now reachable at two paths.
        val tempDirPath = fixture.tempDirPath
        Files.createDirectories(Paths.get(tempDirPath, "node_modules"))
        IoTestUtil.createSymLink("$tempDirPath/model/pkg", "$tempDirPath/node_modules/pkg")

        val tempRoot = requireNotNull(fixture.tempDirFixture.getFile(".")) {
            "fixture.tempDirFixture.getFile(\".\") must resolve the temp root"
        }
        VfsUtil.markDirtyAndRefresh(false, true, true, tempRoot)
        IndexingTestUtil.waitUntilIndexesAreReady(project)

        val realMain = requireNotNull(
            LocalFileSystem.getInstance().refreshAndFindFileByPath("$tempDirPath/model/pkg/Main.tsp"),
        )
        val realUsers = (1..usageFileCount).map { i ->
            requireNotNull(
                LocalFileSystem.getInstance().refreshAndFindFileByPath("$tempDirPath/model/pkg/User$i.tsp"),
            ) { "model/pkg/User$i.tsp must exist" }
        }
        val twinUsers = (1..usageFileCount).map { i ->
            requireNotNull(
                LocalFileSystem.getInstance().refreshAndFindFileByPath("$tempDirPath/node_modules/pkg/User$i.tsp"),
            ) { "node_modules/pkg/User$i.tsp twin must exist" }
        }

        return ManyUsageLayout(tempRoot, realMain, realUsers, twinUsers, tempDirPath)
    }

    private fun assertUsagesRewrittenExactlyOnce(layout: ManyUsageLayout) {
        layout.realUsers.forEachIndexed { index, vf ->
            val i = index + 1
            val text = Files.readString(vf.toNioPath())
            assertEquals(
                "model/pkg/User$i.tsp: expected exactly one occurrence of Bar, got: $text",
                1,
                Regex("\\bBar\\b").findAll(text).count(),
            )
            assertEquals(
                "model/pkg/User$i.tsp: expected zero occurrences of Foo left, got: $text",
                0,
                Regex("\\bFoo\\b").findAll(text).count(),
            )
            val twinPath = Paths.get(layout.tempDirPath, "node_modules", "pkg", "User$i.tsp")
            assertEquals(
                "model/pkg/User$i.tsp and its node_modules twin are the same inode -- must read " +
                    "back identical content",
                text,
                Files.readString(twinPath),
            )
        }
    }

    private fun dispatchAndCollect(action: () -> Unit): Throwable? {
        var thrown: Throwable? = null
        try {
            action()
        } catch (t: Throwable) {
            thrown = t
        }
        // Belt and braces on top of settleVfs's own dispatch call: the oracle
        // (MemoryDiskConflictResolver.askReloadFromDisk) throws from inside an invokeLater
        // runnable (ADR 0014 SS The oracle) -- without pumping the queue here too, a conflict
        // that fires slightly later than settleVfs's own pump would pass this test vacuously.
        try {
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        } catch (t: Throwable) {
            if (thrown == null) thrown = t
        }
        return thrown
    }

    private fun describe(thrown: Throwable?): String =
        thrown?.let {
            generateSequence(it) { c -> c.cause }.joinToString(" -> caused by ") { c -> "${c.javaClass.name}: ${c.message}" }
        }.toString()

    // ---- (1) many usages, opened through the REAL path, no edits: clean --------------------

    fun testA_manyUsages_openedViaRealPath_noConflict() {
        val layout = buildManyUsageLayout()
        layout.realUsers.take(openedUsageFileCount).forEach { fixture.openFileInEditor(it) }

        fixture.configureFromExistingVirtualFile(layout.realMain)
        fixture.editor.caretModel.moveToOffset(fixture.file.text.indexOf("Foo") + 1)

        val thrown = dispatchAndCollect {
            CodeInsightTestUtil.doInlineRename(MemberInplaceRenameHandler(), "Bar", fixture)
            settleVfs(layout.tempRoot)
        }
        assertNull("opening the REAL path with no edits must not conflict: ${describe(thrown)}", thrown)
        assertUsagesRewrittenExactlyOnce(layout)
    }

    // ---- (2) many usages, opened through the TWIN path, no edits: still clean --------------

    fun testB_manyUsages_openedViaTwinPath_noEdits_noConflict() {
        val layout = buildManyUsageLayout()
        layout.twinUsers.take(openedUsageFileCount).forEach { fixture.openFileInEditor(it) }

        fixture.configureFromExistingVirtualFile(layout.realMain)
        fixture.editor.caretModel.moveToOffset(fixture.file.text.indexOf("Foo") + 1)

        val thrown = dispatchAndCollect {
            CodeInsightTestUtil.doInlineRename(MemberInplaceRenameHandler(), "Bar", fixture)
            settleVfs(layout.tempRoot)
        }
        assertNull(
            "a CLEAN cached document on the twin path must not conflict either -- it is just " +
                "silently reloaded on refresh: ${describe(thrown)}",
            thrown,
        )
        assertUsagesRewrittenExactlyOnce(layout)
    }

    // ---- (3) many usages, opened + DIRTIED through the TWIN path, in-place rename ----------

    /**
     * This is the one that reproduces the owner's signature. Deliberately does NOT touch
     * `isVariableInplaceRenameEnabled` -- it stays at its platform default (`true`), exercising
     * the exact path Pins 2 and 3 (in [TypeSpecRenameOnDiskTest]) opted out of.
     */
    fun testC_inplace_dirtyTwinDocument_noConflict() {
        val layout = buildManyUsageLayout()
        val openedTwins = layout.twinUsers.take(openedUsageFileCount)
        openedTwins.forEach { fixture.openFileInEditor(it) }
        openedTwins.forEach { twin ->
            val document = requireNotNull(FileDocumentManager.getInstance().getDocument(twin))
            WriteCommandAction.runWriteCommandAction(project) {
                document.insertString(document.textLength, "\n// dirtied via node_modules twin\n")
            }
        }

        fixture.configureFromExistingVirtualFile(layout.realMain)
        fixture.editor.caretModel.moveToOffset(fixture.file.text.indexOf("Foo") + 1)

        val thrown = dispatchAndCollect {
            CodeInsightTestUtil.doInlineRename(MemberInplaceRenameHandler(), "Bar", fixture)
            settleVfs(layout.tempRoot)
        }

        // REGRESSION PIN (was a tripwire until plan 09 M6.5n): TypeSpecAliasedDocuments
        // .reconcileBeforeWrite (ADR 0015, M6.5m) saves the dirty twin's Document and refreshes
        // its canonical file before the rename writes through it, so the
        // MemoryDiskConflictResolver conflict this test used to require no longer fires.
        assertNull(
            "a dirty cached Document on a node_modules twin of a file the rename rewrites " +
                "through its real path must no longer make MemoryDiskConflictResolver throw " +
                "(ADR 0015): ${describe(thrown)}",
            thrown,
        )

        // Outcome assertions, restored verbatim per plan 09 M6.5n:
        assertUsagesRewrittenExactlyOnce(layout)
        val mainPsi = requireNotNull(PsiManager.getInstance(project).findFile(layout.realMain))
        assertPsiDocumentAndDiskAgree(mainPsi)
        layout.realUsers.take(openedUsageFileCount).forEach { vf ->
            val psi = requireNotNull(PsiManager.getInstance(project).findFile(vf))
            assertPsiDocumentAndDiskAgree(psi)
        }
        val renamedModel = PsiTreeUtil.findChildrenOfType(mainPsi, TypeSpecModelStatement::class.java)
            .firstOrNull { it.name == "Bar" }
        assertNotNull("expected a model named Bar in the declaration file after rename", renamedModel)
    }

    // ---- (4) same starting state (3), driven through the DIALOG path -----------------------

    /**
     * Attribution check: does the same dirty-twin-document setup also break the *dialog* path
     * (`PsiElementRenameHandler` -> `RenameDialog` -> plain `RenameProcessor`, no
     * `MemberInplaceRenamer`/`MyRenameProcessor` anywhere on the call path)? If it does, the
     * cause cannot be either of the two `MemberInplaceRenamer`-specific leads.
     *
     * Measured: it does. Same signature, same `IllegalStateException`. See the class KDoc's
     * "Attribution" section.
     *
     * REGRESSION PIN (was a tripwire until plan 09 M6.5n): `TypeSpecAliasedDocuments
     * .reconcileBeforeWrite` (ADR 0015, M6.5m) runs in `prepareRenaming`, the one seam both the
     * in-place and the dialog rename path pass through (`MemberInplaceRenamer.MyRenameProcessor
     * extends RenameProcessor`), so the fix that closes [testC_inplace_dirtyTwinDocument_noConflict]
     * closes this path too -- both, or it is not a fix (plan 09 M6.5n, ADR 0015 owner ruling 2).
     */
    fun testD_dialogPath_sameDirtyTwinDocument_alsoFixed() {
        val layout = buildManyUsageLayout()
        val openedTwins = layout.twinUsers.take(openedUsageFileCount)
        openedTwins.forEach { fixture.openFileInEditor(it) }
        openedTwins.forEach { twin ->
            val document = requireNotNull(FileDocumentManager.getInstance().getDocument(twin))
            WriteCommandAction.runWriteCommandAction(project) {
                document.insertString(document.textLength, "\n// dirtied via node_modules twin\n")
            }
        }

        fixture.configureFromExistingVirtualFile(layout.realMain)
        fixture.editor.caretModel.moveToOffset(fixture.file.text.indexOf("Foo") + 1)
        // Opt out of in-place explicitly, per Pins 2/3's own convention -- this exercises the
        // dialog/disk-write path, not the live-template one.
        fixture.editor.settings.isVariableInplaceRenameEnabled = false

        val thrown = dispatchAndCollect {
            fixture.renameElementAtCaretUsingHandler("Bar")
            settleVfs(layout.tempRoot)
        }

        assertNull(
            "the dialog path must also no longer raise MemoryDiskConflictResolver's " +
                "IllegalStateException for the identical dirty-twin-document starting state " +
                "(ADR 0015): ${describe(thrown)}",
            thrown,
        )

        assertUsagesRewrittenExactlyOnce(layout)
        val mainPsi = requireNotNull(PsiManager.getInstance(project).findFile(layout.realMain))
        assertPsiDocumentAndDiskAgree(mainPsi)
        layout.realUsers.take(openedUsageFileCount).forEach { vf ->
            val psi = requireNotNull(PsiManager.getInstance(project).findFile(vf))
            assertPsiDocumentAndDiskAgree(psi)
        }
        val renamedModel = PsiTreeUtil.findChildrenOfType(mainPsi, TypeSpecModelStatement::class.java)
            .firstOrNull { it.name == "Bar" }
        assertNotNull("expected a model named Bar in the declaration file after rename", renamedModel)
    }
}
