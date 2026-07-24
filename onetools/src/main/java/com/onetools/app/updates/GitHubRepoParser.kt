package com.onetools.app.updates

import java.net.URI

/** Parse `owner/repo` or https://github.com/owner/repo[/...] into a TrackedApp. */
object GitHubRepoParser {
    fun parse(raw: String, titleOverride: String? = null): Result<TrackedApp> = runCatching {
        val trimmed = raw.trim()
        require(trimmed.isNotEmpty()) { "empty" }

        val (owner, repo) = when {
            trimmed.contains("github.com", ignoreCase = true) -> {
                val normalized = if (trimmed.startsWith("http", ignoreCase = true)) {
                    trimmed
                } else {
                    "https://$trimmed"
                }
                val path = URI(normalized).path.orEmpty()
                val parts = path.trim('/').split('/').filter { it.isNotBlank() }
                require(parts.size >= 2) { "GitHub URL needs owner/repo" }
                parts[0] to parts[1].removeSuffix(".git")
            }
            trimmed.contains('/') -> {
                val parts = trimmed.split('/').filter { it.isNotBlank() }
                require(parts.size >= 2) { "use owner/repo" }
                parts[0] to parts[1].removeSuffix(".git")
            }
            else -> error("use owner/repo or GitHub URL")
        }

        val id = "gh-$owner-$repo".lowercase()
        TrackedApp(
            id = id,
            title = titleOverride?.takeIf { it.isNotBlank() } ?: repo,
            packageName = null,
            githubOwner = owner,
            githubRepo = repo,
            assetPrefer = listOf("arm64-v8a", "release.apk", ".apk"),
            note = "github.com/$owner/$repo",
        )
    }
}
