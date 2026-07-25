package com.onetools.app.updates

/**
 * Clean-room source kinds inspired by Obtainium's OSS-facing set.
 * Does **not** embed Obtainium (GPL-3) code.
 */
enum class AppSource {
    GITHUB,
    GITLAB,
    /** Codeberg / Forgejo / Gitea Releases API. */
    FORGEJO,
    FDROID,
    /** Direct APK URL. */
    DIRECT,
    /** HTML page with APK links (fallback scraper). */
    HTML,
    /** Proprietary OneTools catalog index. */
    ONE_INDEX,
}
