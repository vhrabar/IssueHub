@file:Suppress("UnstableApiUsage")

package com.github.vhrabar.issuehub.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

class ProjectSettingsState : BaseState() {
    var repository by string()
}

/**
 * What IssueHub remembers per project, as opposed to [IssueHubAccounts], which every project shares.
 *
 * Kept in the workspace file rather than `.idea/`: pointing a fork at its upstream is one person's
 * setup, not something to commit for the whole team.
 */
@Service(Service.Level.PROJECT)
@State(name = "IssueHubProject", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class IssueHubProjectSettings : SimplePersistentStateComponent<ProjectSettingsState>(ProjectSettingsState()) {
    /**
     * The repository the user named for this project, used instead of the one detected from the git
     * remote. Null when detection should decide. Its format is up to the provider that reads it.
     */
    var repositoryOverride: String?
        get() = state.repository?.trim()?.ifEmpty { null }
        set(value) {
            state.repository = value?.trim()?.ifEmpty { null }
        }

    companion object {
        fun getInstance(project: Project): IssueHubProjectSettings = project.service()
    }
}
