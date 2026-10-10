package com.github.vhrabar.issuehub.settings

import com.github.vhrabar.issuehub.IssueHubBundle
import com.github.vhrabar.issuehub.provider.IssueProvider
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import javax.swing.JComponent

/**
 * **Settings | Tools | IssueHub | Repository**: names the repository a project reads issues from,
 * for when the git remote points somewhere else (a fork whose issues live upstream) or nowhere a
 * provider recognises.
 *
 */
internal class IssueHubRepositoryConfigurable(
    private val project: Project,
) : Configurable {
    private var field: JBTextField? = null

    override fun getDisplayName(): String = IssueHubBundle["settings.repository.displayName"]

    override fun createComponent(): JComponent {
        val input = JBTextField(COLUMNS).apply { emptyText.text = PLACEHOLDER }
        field = input
        reset()
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty(10)
            add(
                JBPanel<JBPanel<*>>(VerticalLayout(JBUI.scale(GAP))).apply {
                    add(
                        JBPanel<JBPanel<*>>(BorderLayout(JBUI.scale(GAP), 0)).apply {
                            add(JBLabel(IssueHubBundle["settings.repository.label"]), BorderLayout.WEST)
                            add(input, BorderLayout.CENTER)
                        },
                    )
                    add(
                        JBLabel(IssueHubBundle["settings.repository.comment"]).apply {
                            foreground = UIUtil.getContextHelpForeground()
                        },
                    )
                },
                BorderLayout.NORTH,
            )
        }
    }

    override fun isModified(): Boolean = entered() != settings().repositoryOverride

    /** Refuses a repository no provider can read, rather than saving one that would be ignored. */
    override fun apply() {
        val repository = entered()
        if (repository != null) {
            val problems = IssueProvider.EP_NAME.extensionList.map { it.checkRepository(repository) }
            if (problems.none { it == null }) {
                throw ConfigurationException(problems.filterNotNull().first(), IssueHubBundle["settings.repository.displayName"])
            }
        }
        settings().repositoryOverride = repository
    }

    override fun reset() {
        field?.text = settings().repositoryOverride.orEmpty()
    }

    override fun disposeUIResources() {
        field = null
    }

    private fun entered(): String? = field?.text?.trim()?.ifEmpty { null }

    private fun settings() = IssueHubProjectSettings.getInstance(project)

    private companion object {
        const val COLUMNS = 40
        const val GAP = 6
        const val PLACEHOLDER = "owner/name"
    }
}
