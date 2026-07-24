package com.onetools.app.updates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiSourceParserTest {
    @Test
    fun parsesGitlabUrl() {
        val app = GitHubRepoParser.parse(
            "https://gitlab.com/fdroid/fdroidclient/",
            sourceHint = AppSource.GITLAB,
        ).getOrThrow()
        assertEquals(AppSource.GITLAB, app.source)
        assertEquals("fdroid", app.githubOwner)
        assertEquals("fdroidclient", app.githubRepo)
        assertEquals("gitlab.com", app.host)
    }

    @Test
    fun parsesFdroidPackage() {
        val app = GitHubRepoParser.parse(
            "fdroid:org.fdroid.fdroid",
            sourceHint = AppSource.FDROID,
        ).getOrThrow()
        assertEquals(AppSource.FDROID, app.source)
        assertEquals("org.fdroid.fdroid", app.packageName)
    }
}

class HostNormalizeTest {
    @Test
    fun gitlabHost() {
        assertEquals("gitlab.com", GitLabReleaseClient.normalizeHost(null))
        assertEquals("git.example.com", GitLabReleaseClient.normalizeHost("https://git.example.com/"))
    }

    @Test
    fun fdroidBase() {
        assertEquals("https://f-droid.org", FDroidReleaseClient.normalizeBase(null))
        assertEquals("https://mirror.example/fdroid", FDroidReleaseClient.normalizeBase("https://mirror.example/fdroid/"))
    }
}
