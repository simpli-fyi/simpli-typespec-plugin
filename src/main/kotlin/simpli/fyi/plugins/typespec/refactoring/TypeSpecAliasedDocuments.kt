package simpli.fyi.plugins.typespec.refactoring

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.util.IncorrectOperationException
import simpli.fyi.plugins.typespec.TypeSpecFileType
import simpli.fyi.plugins.typespec.resolve.TypeSpecSearchScopes

/**
 * Reconciles a symlink twin's unsaved [com.intellij.openapi.editor.Document] before a refactoring
 * writes through the twin's *real* (canonical) path (ADR 0015, plan 09 M6.5m).
 *
 * In an npm-workspace monorepo `node_modules` holds directory symlinks back into the project's
 * own `model/` sources, so one file on disk can appear in the VFS as two [VirtualFile]s.
 * [FileDocumentManager] is keyed by `VirtualFile` and has no notion of two `VirtualFile`s over one
 * inode; if the user has a dirty, unsaved `Document` cached on the twin, the twin's stale buffer
 * is later flushed over the bytes a rename just wrote through the real path, and
 * `MemoryDiskConflictResolver` — correctly — refuses to pick a winner (a File Cache Conflict
 * dialog in the IDE, an exception in tests). ADR 0013's `getUseScope` narrowing removed the
 * double-write half of this hazard; this removes the aliased-buffer-flush half. Both are needed.
 *
 * **Call this first, before anything is searched.** [TypeSpecRenamePsiElementProcessor.prepareRenaming]
 * is the only seam in the refactoring pipeline where a synchronous save and VFS refresh are both
 * legal: `RenameProcessor.doRun()` calls `prepareRenaming` then `super.doRun()`, which asserts the
 * EDT and then commits documents under progress — so this runs on the EDT, outside any read or
 * write action, before usage search and before that commit.
 * `MemberInplaceRenamer.MyRenameProcessor extends RenameProcessor`, so both the in-place and the
 * dialog rename path arrive here. `RefactoringHelper.prepareOperation` looks like a better seam
 * and is not: it runs inside `ReadAction.compute`, where neither `saveDocument` nor a synchronous
 * refresh is legal (ADR 0015 "Where the seam is").
 *
 * **Forward constraint (ADR 0015 D4): any future write path (safe delete, move, an import-fixing
 * quick fix) must call [reconcileBeforeWrite] before it writes.** This class does not run on its
 * own; the failure mode of skipping it is a dialog storm in a real repository, not a test failure.
 */
object TypeSpecAliasedDocuments {

    /**
     * Walks [FileDocumentManager.unsavedDocuments] — the user's dirty buffers, a handful, never a
     * directory or `node_modules` walk — and for each one cached on a `.tsp` [VirtualFile] `V`
     * whose [VirtualFile.getCanonicalFile] `C` differs from `V` and lies in
     * [TypeSpecSearchScopes.tspScope] (i.e. `V` is an alias shadowing real project source a
     * rename may rewrite):
     *
     * - refuses if `C` *also* has a cached, unsaved `Document` — two live buffers over one inode
     *   with different contents; any winner picked here would silently destroy the other;
     * - refuses if `C` is the containing file of [primary] or of any element already in
     *   [allRenames] — saving there would change the bytes under the refactoring's own primary
     *   element, and the following refresh would invalidate it mid-rename;
     * - otherwise saves `V`'s document (`WriteAction`, per document) so the rename applies on top
     *   of the user's real, unsaved work rather than discarding it.
     *
     * After the loop, once, still on the EDT and outside any write action: a single synchronous,
     * non-recursive `VfsUtil.markDirtyAndRefresh` over the reconciled canonical files, then
     * `PsiDocumentManager.commitAllDocuments()`.
     *
     * Idempotent: `RenameProcessor.doRun` calls `prepareRenaming` again for every automatic-renamer
     * element, but after the first pass the reconciled documents are no longer in
     * `unsavedDocuments`, so a second pass over the same twins is a no-op.
     */
    fun reconcileBeforeWrite(
        project: Project,
        primary: PsiElement,
        allRenames: Map<PsiElement, String>,
    ) {
        val fdm = FileDocumentManager.getInstance()
        val scope = TypeSpecSearchScopes.tspScope(project)
        val protected: Set<VirtualFile> = (listOf(primary) + allRenames.keys)
            .mapNotNull { it.containingFile?.virtualFile }
            .map { it.canonicalFile ?: it }
            .toSet()

        val toRefresh = mutableListOf<VirtualFile>()
        // `unsavedDocuments` is a snapshot; saving mutates it as we go, so copy before iterating.
        for (document in fdm.unsavedDocuments.toList()) {
            val twin = fdm.getFile(document) ?: continue
            if (!twin.isValid || twin.fileType != TypeSpecFileType.INSTANCE) continue
            val real = twin.canonicalFile ?: continue // recurses through a symlinked parent
            if (real == twin) continue // not an alias -- the overwhelmingly common case
            if (!scope.contains(real)) continue // alias does not shadow project .tsp source

            val realDocument = fdm.getCachedDocument(real)
            if (realDocument != null && fdm.isDocumentUnsaved(realDocument)) {
                throw IncorrectOperationException(
                    "Cannot rename: both '${real.path}' and its symlinked alias " +
                        "'${twin.path}' have unsaved changes open in an editor. Save or revert " +
                        "one of the two editors and retry.",
                )
            }
            if (real in protected) {
                throw IncorrectOperationException(
                    "Cannot rename: '${real.path}' is the file being renamed, and its symlinked " +
                        "alias '${twin.path}' has unsaved changes open in an editor. Save or " +
                        "revert that editor and retry.",
                )
            }

            WriteAction.run<RuntimeException> { fdm.saveDocument(document) }
            toRefresh += real
        }

        if (toRefresh.isEmpty()) return
        VfsUtil.markDirtyAndRefresh(false, false, false, *toRefresh.toTypedArray())
        PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
}
