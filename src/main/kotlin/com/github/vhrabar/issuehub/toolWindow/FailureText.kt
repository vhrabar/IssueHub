package com.github.vhrabar.issuehub.toolWindow

import com.github.vhrabar.issuehub.IssueHubBundle
import com.github.vhrabar.issuehub.provider.NetworkUnavailableException
import com.github.vhrabar.issuehub.provider.RateLimitedException
import com.intellij.util.text.DateFormatUtil

/**
 * One line on what went wrong, for the issue list and the detail view alike.
 *
 * A rate limit is spelled out as the time it lifts and an unreachable server by name, rather than
 * as the exception's own text; anything else falls back to the provider's message.
 * [providerName] stands in for the server when the failure doesn't name one.
 */
internal fun describeFailure(
    error: Throwable,
    providerName: String,
): String =
    when (error) {
        is RateLimitedException -> {
            val at = DateFormatUtil.formatTime(error.resetAt.toEpochMilli())
            IssueHubBundle[if (error.authenticated) "toolWindow.rateLimited" else "toolWindow.rateLimited.anonymous", at]
        }

        is NetworkUnavailableException -> {
            val server = error.server ?: providerName
            when (error.kind) {
                NetworkUnavailableException.Kind.OFFLINE -> IssueHubBundle["network.offline", server]
                NetworkUnavailableException.Kind.TIMEOUT -> IssueHubBundle["network.timeout", server]
            }
        }

        else -> {
            error.message ?: error.toString()
        }
    }
