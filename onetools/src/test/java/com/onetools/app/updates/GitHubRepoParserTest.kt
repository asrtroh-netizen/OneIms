package com.onetools.app.updates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubRepoParserTest {
    @Test
    fun parsesOwnerRepo() {
        val app = GitHubRepoParser.parse("asrtroh-netizen/OneIms").getOrThrow()
        assertEquals("asrtroh-netizen", app.githubOwner)
        assertEquals("OneIms", app.githubRepo)
        assertTrue(app.id.contains("oneims"))
    }

    @Test
    fun parsesUrl() {
        val app = GitHubRepoParser.parse(
            "https://github.com/asrtroh-netizen/OneIms/",
            titleOverride = "OneIms",
        ).getOrThrow()
        assertEquals("asrtroh-netizen", app.githubOwner)
        assertEquals("OneIms", app.githubRepo)
        assertEquals("OneIms", app.title)
    }
}
