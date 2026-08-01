package com.github.vhrabar.issuehub.editor

import com.github.vhrabar.issuehub.toolWindow.IssueDetailPanel
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import javax.swing.JComponent

/** Hosts [IssueDetailPanel] as an editor, which is all the platform needs to give it a tab. */
internal class IssueFileEditor(
    project: Project,
    private val file: IssueVirtualFile,
) : UserDataHolderBase(),
    FileEditor {
    private val panel = IssueDetailPanel(project, file.issue)

    private val listeners = PropertyChangeSupport(this)

    override fun getComponent(): JComponent = panel

    override fun getPreferredFocusedComponent(): JComponent = panel.preferredFocusComponent

    override fun getName(): String = file.issue.displayNumber

    override fun getFile(): VirtualFile = file

    override fun setState(state: FileEditorState) = Unit

    override fun isModified(): Boolean = false

    override fun isValid(): Boolean = file.isValid

    override fun addPropertyChangeListener(listener: PropertyChangeListener) = listeners.addPropertyChangeListener(listener)

    override fun removePropertyChangeListener(listener: PropertyChangeListener) = listeners.removePropertyChangeListener(listener)

    override fun dispose() = panel.dispose()
}
