package com.github.vhrabar.issuehub.model

/** Provider-neutral issue states */
enum class IssueState { OPEN, CLOSED, OTHER}

data class IssueLabel(
    val name: String,
    val color: String?=null,
)

/** Provider-neutral repr. of tracked issues */
data class Issue(
    val id: Int,
    val displayNumber: String,
    val title: String,
    val state: IssueState,
    val body: String? = null,
    val labels: List<IssueLabel> = emptyList(),
    val assignee: String? = null,
    val url: String,
    val updatedAt: String,
)

/* placeholder for queries (50 last opened) */
data class IssueQuery(
    val state: IssueState = IssueState.OPEN,
    val limit: Int = 50,
)