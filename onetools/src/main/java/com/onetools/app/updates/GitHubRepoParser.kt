package com.onetools.app.updates

import java.net.URI

/** Parse GitHub / GitLab / F-Droid inputs into TrackedApp. */
object GitHubRepoParser {
    fun parse(
        raw: String,
        titleOverride: String? = null,
        sourceHint: AppSource = AppSource.GITHUB,
        packageName: String? = null,
        hostOverride: String? = null,
    ): Result<TrackedApp> = runCatching {
        val trimmed = raw.trim()
        require(trimmed.isNotEmpty()) { "empty" }

        val detected = detectSource(trimmed, sourceHint)
        when (detected) {
            AppSource.FDROID -> parseFdroid(trimmed, titleOverride, packageName, hostOverride)
            AppSource.GITLAB -> parseGitlab(trimmed, titleOverride, packageName, hostOverride)
            AppSource.ONE_INDEX -> parseOneIndex(trimmed, titleOverride, packageName, hostOverride)
            AppSource.GITHUB -> parseGithub(trimmed, titleOverride, packageName)
        }
    }

    private fun detectSource(raw: String, hint: AppSource): AppSource {
        val lower = raw.lowercase()
        return when {
            lower.startsWith("one:") || lower.startsWith("onetools:") ||
                lower.endsWith("one-update.json") || lower.contains("onetools.update") ->
                AppSource.ONE_INDEX
            lower.startsWith("fdroid:") || lower.contains("f-droid.org") -> AppSource.FDROID
            lower.contains("gitlab.") || lower.contains("gitlab.com") -> AppSource.GITLAB
            lower.contains("github.com") -> AppSource.GITHUB
            else -> hint
        }
    }

    private fun parseGithub(raw: String, titleOverride: String?, packageName: String?): TrackedApp {
        val (owner, repo) = ownerRepoFrom(raw, defaultHost = "github.com")
        return TrackedApp(
            id = "gh-$owner-$repo".lowercase(),
            title = titleOverride?.takeIf { it.isNotBlank() } ?: repo,
            packageName = packageName?.takeIf { it.isNotBlank() },
            githubOwner = owner,
            githubRepo = repo,
            assetPrefer = listOf("arm64-v8a", "release.apk", ".apk"),
            note = "github.com/$owner/$repo",
            source = AppSource.GITHUB,
        )
    }

    private fun parseGitlab(
        raw: String,
        titleOverride: String?,
        packageName: String?,
        hostOverride: String?,
    ): TrackedApp {
        val host = hostOverride?.takeIf { it.isNotBlank() }
            ?: hostFromUrl(raw)
            ?: "gitlab.com"
        val (owner, repo) = ownerRepoFrom(raw, defaultHost = host)
        return TrackedApp(
            id = "gl-$host-$owner-$repo".lowercase().replace('.', '-'),
            title = titleOverride?.takeIf { it.isNotBlank() } ?: repo,
            packageName = packageName?.takeIf { it.isNotBlank() },
            githubOwner = owner,
            githubRepo = repo,
            assetPrefer = listOf("arm64-v8a", "release.apk", ".apk"),
            note = "$host/$owner/$repo",
            source = AppSource.GITLAB,
            host = host,
        )
    }

    private fun parseOneIndex(
        raw: String,
        titleOverride: String?,
        packageName: String?,
        hostOverride: String?,
    ): TrackedApp {
        val url = when {
            !hostOverride.isNullOrBlank() -> hostOverride.trim()
            raw.startsWith("one:", ignoreCase = true) -> raw.substringAfter(':').trim()
            raw.startsWith("onetools:", ignoreCase = true) -> raw.substringAfter(':').trim()
            else -> raw.trim()
        }
        require(url.startsWith("http", ignoreCase = true)) { "One Index 需要 https://.../xxx.json" }
        val idHint = packageName?.takeIf { it.isNotBlank() }
            ?: titleOverride?.takeIf { it.isNotBlank() }
            ?: url.substringAfterLast('/').removeSuffix(".json")
        return TrackedApp(
            id = "one-$idHint".lowercase().replace(' ', '-'),
            title = titleOverride?.takeIf { it.isNotBlank() } ?: idHint,
            packageName = packageName?.takeIf { it.isNotBlank() },
            githubOwner = "one-index",
            githubRepo = idHint,
            assetPrefer = listOf(".apk"),
            note = "One Index · ${OneIndexClient.SCHEMA}",
            source = AppSource.ONE_INDEX,
            host = url,
        )
    }

    private fun parseFdroid(
        raw: String,
        titleOverride: String?,
        packageName: String?,
        hostOverride: String?,
    ): TrackedApp {
        val pkg = when {
            !packageName.isNullOrBlank() -> packageName.trim()
            raw.startsWith("fdroid:", ignoreCase = true) -> raw.substringAfter(':').trim()
            raw.contains("f-droid.org/packages/", ignoreCase = true) ->
                raw.substringAfter("packages/").trim('/').substringBefore('/')
            else -> raw.trim()
        }
        require(pkg.isNotBlank() && pkg.contains('.')) { "F-Droid 需要包名，如 org.app" }
        val base = hostOverride?.takeIf { it.isNotBlank() } ?: "https://f-droid.org"
        return TrackedApp(
            id = "fd-$pkg".lowercase(),
            title = titleOverride?.takeIf { it.isNotBlank() } ?: pkg.substringAfterLast('.'),
            packageName = pkg,
            githubOwner = "fdroid",
            githubRepo = pkg,
            assetPrefer = listOf(".apk"),
            note = "F-Droid · $pkg",
            source = AppSource.FDROID,
            host = base,
        )
    }

    private fun hostFromUrl(raw: String): String? {
        if (!raw.contains("://") && !raw.contains('.')) return null
        val normalized = if (raw.startsWith("http", ignoreCase = true)) raw else "https://$raw"
        return runCatching { URI(normalized).host }.getOrNull()
    }

    private fun ownerRepoFrom(raw: String, defaultHost: String): Pair<String, String> {
        return when {
            raw.contains("://") || raw.contains(defaultHost, ignoreCase = true) ||
                raw.contains("github.com", ignoreCase = true) ||
                raw.contains("gitlab.", ignoreCase = true) -> {
                val normalized = if (raw.startsWith("http", ignoreCase = true)) raw else "https://$raw"
                val path = URI(normalized).path.orEmpty()
                val parts = path.trim('/').split('/').filter { it.isNotBlank() }
                require(parts.size >= 2) { "URL needs owner/repo" }
                parts[0] to parts[1].removeSuffix(".git")
            }
            raw.contains('/') -> {
                val parts = raw.split('/').filter { it.isNotBlank() }
                require(parts.size >= 2) { "use owner/repo" }
                parts[0] to parts[1].removeSuffix(".git")
            }
            else -> error("use owner/repo or URL")
        }
    }
}
