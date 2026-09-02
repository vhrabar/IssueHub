package com.github.vhrabar.issuehub.provider.github

import com.github.vhrabar.issuehub.model.PullRequestState
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubGraphQlDtoTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

    @Test
    fun `linked pull requests keep the state the sidebar colours them by`() {
        val development = development(DEVELOPMENT_JSON).toDevelopment()

        assertEquals(listOf("#42", "#40"), development.pullRequests.map { it.displayNumber })
        // A draft is an open pull request GitHub draws differently, so it can't stay merely OPEN.
        assertEquals(
            listOf(PullRequestState.DRAFT, PullRequestState.MERGED),
            development.pullRequests.map { it.state },
        )
        assertEquals("Add the sidebar", development.pullRequests.first().title)
    }

    /** GraphQL names the branch's repository, not its page, so the address has to be built. */
    @Test
    fun `a linked branch points at its page on the repository it lives in`() {
        val branch = development(DEVELOPMENT_JSON).toDevelopment().branches.single()

        assertEquals("42-sidebar", branch.name)
        assertEquals("https://github.test/octocat/hello-world/tree/42-sidebar", branch.url)
    }

    @Test
    fun `an issue with nothing linked reads as empty rather than unknown`() {
        val development = development("""{"data": {"repository": {"issue": {}}}}""").toDevelopment()

        assertTrue(development.isEmpty)
    }

    @Test
    fun `a board contributes its own fields, whatever their type`() {
        val project = projects(PROJECTS_JSON).toProjectItems().single()

        assertEquals("Roadmap", project.title)
        assertEquals("https://github.test/orgs/octocat/projects/3", project.url)
        assertEquals(
            listOf(
                "Status" to "In Progress",
                "Size" to "3",
                "Estimate" to "1.5",
                "Start date" to "2026-08-03",
                "Target" to "2026-08-17",
                "Iteration" to "Sprint 12",
                "Notes" to "Needs design input",
            ),
            project.fields.map { it.name to it.value },
        )
    }

    /**
     * Value types the query doesn't ask about come back as empty objects, and a field nobody has
     * filled in carries no value; neither is something to put on screen.
     */
    @Test
    fun `field values with nothing in them are dropped`() {
        val fields = projects(PROJECTS_JSON).toProjectItems().single().fields

        assertNull(fields.firstOrNull { it.name == "Repository" })
        assertEquals(fields.size, fields.distinctBy { it.name }.size)
    }

    @Test
    fun `an issue on no board yields no projects`() {
        assertEquals(
            emptyList<Any>(),
            projects("""{"data": {"repository": {"issue": {"projectItems": {"nodes": []}}}}}""").toProjectItems(),
        )
    }

    private fun development(payload: String): GitHubDevelopmentDto =
        json
            .decodeFromString<GitHubGraphQlResponse<GitHubRepositoryDataDto<GitHubDevelopmentDto>>>(payload)
            .data!!
            .repository!!
            .issue!!

    private fun projects(payload: String): GitHubProjectItemsDto =
        json
            .decodeFromString<GitHubGraphQlResponse<GitHubRepositoryDataDto<GitHubProjectItemsDto>>>(payload)
            .data!!
            .repository!!
            .issue!!

    private companion object {
        val DEVELOPMENT_JSON =
            """
            {
              "data": {
                "repository": {
                  "issue": {
                    "closedByPullRequestsReferences": {
                      "nodes": [
                        {
                          "number": 42,
                          "title": "Add the sidebar",
                          "url": "https://github.test/octocat/hello-world/pull/42",
                          "state": "OPEN",
                          "isDraft": true
                        },
                        {
                          "number": 40,
                          "title": "Groundwork",
                          "url": "https://github.test/octocat/hello-world/pull/40",
                          "state": "MERGED",
                          "isDraft": false
                        }
                      ]
                    },
                    "linkedBranches": {
                      "nodes": [
                        {
                          "ref": {
                            "name": "42-sidebar",
                            "repository": { "url": "https://github.test/octocat/hello-world" }
                          }
                        }
                      ]
                    }
                  }
                }
              }
            }
            """.trimIndent()

        val PROJECTS_JSON =
            """
            {
              "data": {
                "repository": {
                  "issue": {
                    "projectItems": {
                      "nodes": [
                        {
                          "project": {
                            "title": "Roadmap",
                            "url": "https://github.test/orgs/octocat/projects/3"
                          },
                          "fieldValues": {
                            "nodes": [
                              {},
                              { "name": "In Progress", "field": { "name": "Status" } },
                              { "number": 3.0, "field": { "name": "Size" } },
                              { "number": 1.5, "field": { "name": "Estimate" } },
                              { "date": "2026-08-03", "field": { "name": "Start date" } },
                              { "date": "2026-08-17", "field": { "name": "Target" } },
                              { "title": "Sprint 12", "field": { "name": "Iteration" } },
                              { "text": "Needs design input", "field": { "name": "Notes" } },
                              { "field": { "name": "Repository" } }
                            ]
                          }
                        }
                      ]
                    }
                  }
                }
              }
            }
            """.trimIndent()
    }
}
