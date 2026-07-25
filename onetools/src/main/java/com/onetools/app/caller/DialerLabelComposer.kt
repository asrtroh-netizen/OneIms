package com.onetools.app.caller

/**
 * Compose the single line shown in Pixel Phone dialer / incoming call UI.
 * Goal: cleaner than typical "spam app" clutter — short, native-feeling Chinese.
 */
object DialerLabelComposer {
    data class Result(val displayName: String, val label: String)

    fun compose(
        geo: CnMobileGeo.Hit?,
        ruleKind: CallRuleKind?,
        ruleTag: String?,
        fallbackAllow: String,
        fallbackLabel: String,
        fallbackBlock: String,
        spamFmt: (String) -> String,
    ): Result? {
        val tag = ruleTag?.trim().orEmpty()
        val geoLine = geo?.dialerLine().orEmpty()
        return when (ruleKind) {
            CallRuleKind.ALLOW -> {
                val name = tag.ifBlank { fallbackAllow }
                Result(join(name, geoLine), name)
            }
            CallRuleKind.LABEL -> {
                val name = tag.ifBlank { fallbackLabel }
                Result(join(name, geoLine), name)
            }
            CallRuleKind.BLOCK -> {
                val base = tag.ifBlank { fallbackBlock }
                val spam = spamFmt(base)
                Result(join(spam, geoLine), base)
            }
            null -> {
                if (geoLine.isBlank()) null
                else Result(geoLine, geoLine)
            }
        }
    }

    private fun join(primary: String, geo: String): String = when {
        primary.isBlank() -> geo
        geo.isBlank() -> primary
        primary.contains(geo) || geo.contains(primary) -> primary
        else -> "$primary · $geo"
    }
}
