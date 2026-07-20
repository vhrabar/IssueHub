package com.github.vhrabar.issuehub

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class IssueHubPluginTest : BasePlatformTestCase() {

    fun testBundleMessageResolves() {
        assertEquals("IssueHub", IssueHubBundle["name"])
    }
}