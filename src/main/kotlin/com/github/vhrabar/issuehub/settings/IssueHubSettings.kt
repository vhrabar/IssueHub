@file:Suppress("UnstableApiUsage")

package com.github.vhrabar.issuehub.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

class AppSettingsState : BaseState() {
    var autoRefreshMinutes by property(0)
}

/** Preferences that apply to every project, as opposed to [IssueHubProjectSettings]. */
@Service(Service.Level.APP)
@State(name = "IssueHubSettings", storages = [Storage("issuehub.xml")])
class IssueHubSettings : SimplePersistentStateComponent<AppSettingsState>(AppSettingsState()) {
    /** How often a visible issue list reloads itself, in minutes; 0 leaves it to the user. */
    var autoRefreshMinutes: Int
        get() = state.autoRefreshMinutes
        set(value) {
            state.autoRefreshMinutes = value
        }

    companion object {
        /** What the settings page offers, 0 being off. */
        val AUTO_REFRESH_CHOICES = listOf(0, 5, 15, 30)

        fun getInstance(): IssueHubSettings = service()
    }
}
