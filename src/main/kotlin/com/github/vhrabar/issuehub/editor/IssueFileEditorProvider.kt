package com.github.vhrabar.issuehub.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Claims [IssueVirtualFile] for [IssueFileEditor].
 *
 * [FileEditorPolicy.HIDE_DEFAULT_EDITOR] keeps the platform from offering its own view of the file's
 * (empty) contents as a second tab next to ours.
 */
class IssueFileEditorProvider :
    FileEditorProvider,
    DumbAware {
    override fun accept(
        project: Project,
        file: VirtualFile,
    ): Boolean = file is IssueVirtualFile

    override fun acceptRequiresReadAction(): Boolean = false

    override fun createEditor(
        project: Project,
        file: VirtualFile,
    ): FileEditor = IssueFileEditor(project, file as IssueVirtualFile)

    override fun getEditorTypeId(): String = "issuehub.issue"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}
