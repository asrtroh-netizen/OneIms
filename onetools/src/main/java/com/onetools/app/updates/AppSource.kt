package com.onetools.app.updates

/** First-party source kinds for One 自主更新中心. */
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
