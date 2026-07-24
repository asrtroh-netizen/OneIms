package com.onetools.app.updates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OneIndexClientTest {
    private val sample = """
        {
          "schema": "onetools.update.v1",
          "generatedAt": "2026-07-25T00:00:00Z",
          "apps": [{
            "id": "onetools",
            "title": "OneTools",
            "packageName": "com.onetools.app",
            "versionName": "0.1.0",
            "apkUrl": "https://cdn.example/onetools.apk",
            "apkName": "onetools.apk",
            "changelog": "hi"
          }]
        }
    """.trimIndent()

    @Test
    fun appsFromIndexJson() {
        val apps = OneIndexClient.appsFromIndexJson(sample, "https://cdn.example/one-update.json")
        assertEquals(1, apps.size)
        assertEquals(AppSource.ONE_INDEX, apps[0].source)
        assertEquals("com.onetools.app", apps[0].packageName)
        assertEquals("https://cdn.example/one-update.json", apps[0].host)
    }

    @Test
    fun parseOneUrl() {
        val app = GitHubRepoParser.parse(
            "one:https://cdn.example/one-update.json",
            titleOverride = "OneTools",
            sourceHint = AppSource.ONE_INDEX,
            packageName = "com.onetools.app",
        ).getOrThrow()
        assertEquals(AppSource.ONE_INDEX, app.source)
        assertEquals("https://cdn.example/one-update.json", app.host)
        assertTrue(app.note.contains("onetools.update.v1"))
    }
}
