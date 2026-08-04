package com.github.vhrabar.issuehub.editor

import com.github.vhrabar.issuehub.IssueHubBundle
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * The type worn by [IssueVirtualFile].
 */
internal object IssueFileType : FileType {
    private val icon = IconLoader.getIcon("/icons/issueHub.svg", IssueFileType::class.java)

    override fun getName(): String = "IssueHub Issue"

    override fun getDescription(): String = IssueHubBundle["editor.fileType.description"]

    override fun getDefaultExtension(): String = ""

    override fun getIcon(): Icon = icon

    override fun isBinary(): Boolean = true

    override fun isReadOnly(): Boolean = true
}
