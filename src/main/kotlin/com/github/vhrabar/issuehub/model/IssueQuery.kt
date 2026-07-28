package com.github.vhrabar.issuehub.model

/** Which issue states a query should return. */
enum class IssueStateFilter { OPEN, CLOSED, ALL }

/** The field a result list is ordered by. */
enum class IssueSortField { CREATED, UPDATED, COMMENTS }

enum class IssueSortDirection { ASC, DESC }

/**
 * Assignee restriction.
 */
sealed interface AssigneeFilter {
    data object Unassigned : AssigneeFilter

    data class User(
        val login: String,
    ) : AssigneeFilter
}

/** Milestone restriction; */
sealed interface MilestoneFilter {
    data object None : MilestoneFilter

    data class Named(
        val milestone: IssueMilestone,
    ) : MilestoneFilter
}

/**
 * A search/filter/sort request against a provider.
 *
 */
data class IssueQuery(
    val state: IssueStateFilter = IssueStateFilter.OPEN,
    val text: String = "",
    val labels: Set<String> = emptySet(),
    val assignee: AssigneeFilter? = null,
    val author: String? = null,
    val milestone: MilestoneFilter? = null,
    val sortField: IssueSortField = IssueSortField.CREATED,
    val sortDirection: IssueSortDirection = IssueSortDirection.DESC,
    val limit: Int = 50,
) {
    /** True when the query narrows the result set beyond the default "all open issues" view. */
    val isFiltered: Boolean
        get() =
            state != IssueStateFilter.OPEN ||
                text.isNotBlank() ||
                labels.isNotEmpty() ||
                assignee != null ||
                author != null ||
                milestone != null
}

/**
 * The values a user can pick from in the filter UI, as reported by the provider.
 *
 * Any list may be empty: providers that cannot enumerate a dimension (or whose token lacks the
 * permission to) simply offer no choices for it rather than failing the whole refresh.
 */
data class IssueFilterOptions(
    val labels: List<IssueLabel> = emptyList(),
    val assignees: List<String> = emptyList(),
    val milestones: List<IssueMilestone> = emptyList(),
    val authors: List<String> = emptyList(),
) {
    /** Union with [other], de-duplicated and alphabetically ordered. */
    fun mergedWith(other: IssueFilterOptions): IssueFilterOptions =
        IssueFilterOptions(
            labels = (labels + other.labels).distinctBy { it.name }.sortedBy { it.name.lowercase() },
            assignees = (assignees + other.assignees).distinct().sortedBy(String::lowercase),
            milestones = (milestones + other.milestones).distinctBy { it.number }.sortedBy { it.title.lowercase() },
            authors = (authors + other.authors).distinct().sortedBy(String::lowercase),
        )
}

/**
 * The filter values visible on an already-loaded page of issues.
 *
 */
fun optionsFrom(issues: List<Issue>): IssueFilterOptions =
    IssueFilterOptions(
        labels = issues.flatMap { it.labels },
        assignees = issues.mapNotNull { it.assignee },
        milestones = issues.mapNotNull { it.milestone },
        authors = issues.mapNotNull { it.author },
    ).mergedWith(IssueFilterOptions())
