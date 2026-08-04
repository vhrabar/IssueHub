package com.github.vhrabar.issuehub.editor

import com.github.vhrabar.issuehub.model.Issue
import com.github.vhrabar.issuehub.toolWindow.IssueStateIcon
import com.intellij.ide.FileIconProvider
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import javax.swing.Icon

/**
 * An issue dressed up as a file, so the platform opens it in the editor area — full width, splittable
 * and side by side with source — instead of inside the tool window.
 *
 * The file carries no content: [IssueFileEditor] draws the issue, and the panel re-reads it from the
 * provider, so there is nothing here to keep in sync.
 */
internal class IssueVirtualFile(
    val issue: Issue,
) : LightVirtualFile(issue.displayNumber, IssueFileType, "") {
    init {
        isWritable = false
    }

    /** Editor tabs are titled from this; the full title lives in the panel header. */
    override fun getPresentableName(): String = issue.displayNumber

    companion object {
        /** Opens [issue] in the editor area, focusing the tab it already has instead of stacking a second one. */
        fun open(
            project: Project,
            issue: Issue,
        ) {
            val manager = FileEditorManager.getInstance(project)
            val open = manager.openFiles.firstOrNull { it is IssueVirtualFile && it.issue.id == issue.id }
            manager.openFile(open ?: IssueVirtualFile(issue), true)
        }
    }
}

/** Gives issue tabs the same state dot the list rows use, in place of the generic plugin icon. */
class IssueFileIconProvider : FileIconProvider {
    override fun getIcon(
        file: VirtualFile,
        flags: Int,
        project: Project?,
    ): Icon? = (file as? IssueVirtualFile)?.let { IssueStateIcon(it.issue.state, TAB_ICON_SIZE) }

    private companion object {
        /** Editor tabs and the project view both budget 16px for a file icon. */
        const val TAB_ICON_SIZE = 16
    }
}
