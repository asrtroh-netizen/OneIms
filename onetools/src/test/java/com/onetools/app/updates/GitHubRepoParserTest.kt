package com.onetools.app.updates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubRepoParserTest {
    @Test
    fun parsesOwnerRepo() {
        val app = GitHubRepoParser.parse("OneCatx/OneIms").getOrThrow()
        assertEquals("OneCatx", app.githubOwner)
        assertEquals("OneIms", app.githubRepo)
        assertTrue(app.id.contains("oneims"))
    }

    @Test
    fun parsesUrl() {
        val app = GitHubRepoParser.parse(
            "https://github.com/ImranR98/Obtainium/",
            titleOverride = "Obtainium",
        ).getOrThrow()
        assertEquals("ImranR98", app.githubOwner)
        assertEquals("Obtainium", app.githubRepo)
        assertEquals("Obtainium", app.title)
    }
}
