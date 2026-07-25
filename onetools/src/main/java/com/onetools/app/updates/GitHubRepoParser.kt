package com.onetools.app.updates

import java.net.URI

/** Parse GitHub / GitLab / Forgejo / F-Droid / Direct / HTML inputs into TrackedApp. */
object GitHubRepoParser {
    fun parse(
        raw: String,
        titleOverride: String? = null,
        sourceHint: AppSource = AppSource.GITHUB,
        packageName: String? = null,
        hostOverride: String? = null,
        apkRegex: String? = null,
        includePrereleases: Boolean = false,
    ): Result<TrackedApp> = runCatching {
        val trimmed = raw.trim()
        require(trimmed.isNotEmpty()) { "empty" }

        val detected = detectSource(trimmed, sourceHint)
        val base = when (detected) {
            AppSource.FDROID -> parseFdroid(trimmed, titleOverride, packageName, hostOverride)
            AppSource.GITLAB -> parseGitlab(trimmed, titleOverride, packageName, hostOverride)
            AppSource.FORGEJO -> parseForgejo(trimmed, titleOverride, packageName, hostOverride)
            AppSource.ONE_INDEX -> parseOneIndex(trimmed, titleOverride, packageName, hostOverride)
            AppSource.DIRECT -> parseDirect(trimmed, titleOverride, packageName)
            AppSource.HTML -> parseHtml(trimmed, titleOverride, packageName)
            AppSource.GITHUB -> parseGithub(trimmed, titleOverride, packageName)
        }
        base.copy(
            apkRegex = apkRegex?.trim()?.takeIf { it.isNotEmpty() },
            includePrereleases = includePrereleases,
        )
    }

    private fun detectSource(raw: String, hint: AppSource): AppSource {
        val lower = raw.lowercase()
        return when {
            lower.startsWith("one:") || lower.startsWith("onetools:") ||
                lower.endsWith("one-update.json") || lower.contains("onetools.update") ->
                AppSource.ONE_INDEX
            lower.startsWith("fdroid:") || lower.contains("f-droid.org") -> AppSource.FDROID
            lower.contains("gitlab.") || lower.contains("gitlab.com") -> AppSource.GITLAB
            lower.contains("codeberg.org") || lower.contains("forgejo") ||
                lower.startsWith("forgejo:") -> AppSource.FORGEJO
            lower.endsWith(".apk") || lower.contains(".apk?") -> AppSource.DIRECT
            lower.startsWith("html:") -> AppSource.HTML
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

    private fun parseForgejo(
        raw: String,
        titleOverride: String?,
        packageName: String?,
        hostOverride: String?,
    ): TrackedApp {
        val cleaned = raw.removePrefix("forgejo:").trim()
        val host = hostOverride?.takeIf { it.isNotBlank() }
            ?: hostFromUrl(cleaned)
            ?: "codeberg.org"
        val (owner, repo) = ownerRepoFrom(cleaned, defaultHost = host)
        return TrackedApp(
            id = "fj-$host-$owner-$repo".lowercase().replace('.', '-'),
            title = titleOverride?.takeIf { it.isNotBlank() } ?: repo,
            packageName = packageName?.takeIf { it.isNotBlank() },
            githubOwner = owner,
            githubRepo = repo,
            assetPrefer = listOf("arm64-v8a", "release.apk", ".apk"),
            note = "$host/$owner/$repo",
            source = AppSource.FORGEJO,
            host = host,
        )
    }

    private fun parseDirect(
        raw: String,
        titleOverride: String?,
        packageName: String?,
    ): TrackedApp {
        val url = if (raw.startsWith("http")) raw else "https://$raw"
        require(url.contains(".apk", ignoreCase = true)) { "Direct 需要 .apk URL" }
        val name = url.substringAfterLast('/').substringBefore('?').ifBlank { "app.apk" }
        return TrackedApp(
            id = "direct-${name.lowercase().hashCode().toUInt().toString(16)}",
            title = titleOverride?.takeIf { it.isNotBlank() } ?: name.removeSuffix(".apk"),
            packageName = packageName?.takeIf { it.isNotBlank() },
            githubOwner = "direct",
            githubRepo = name,
            assetPrefer = listOf(".apk"),
            note = "Direct APK",
            source = AppSource.DIRECT,
            host = url,
        )
    }

    private fun parseHtml(
        raw: String,
        titleOverride: String?,
        packageName: String?,
    ): TrackedApp {
        val url = raw.removePrefix("html:").trim().let {
            if (it.startsWith("http")) it else "https://$it"
        }
        require(url.startsWith("http")) { "HTML 需要页面 URL" }
        val hint = url.substringAfterLast('/').ifBlank { "page" }
        return TrackedApp(
            id = "html-${hint.lowercase().hashCode().toUInt().toString(16)}",
            title = titleOverride?.takeIf { it.isNotBlank() } ?: hint,
            packageName = packageName?.takeIf { it.isNotBlank() },
            githubOwner = "html",
            githubRepo = hint,
            assetPrefer = listOf(".apk"),
            note = "HTML fallback",
            source = AppSource.HTML,
            host = url,
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
                raw.contains("gitlab.", ignoreCase = true) ||
                raw.contains("codeberg.org", ignoreCase = true) -> {
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
