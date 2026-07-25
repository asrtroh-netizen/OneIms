package com.onetools.app.caller

/**
 * Compose dialer Directory fields for Pixel Phone.
 * Product: unsaved numbers keep the phone number as DISPLAY_NAME;
 * geo / tags go to LABEL — never invent a geo string as the contact name.
 */
object DialerLabelComposer {
    data class Result(val displayName: String, val label: String)

    fun compose(
        numberDisplay: String,
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
        val number = numberDisplay.trim()
        return when (ruleKind) {
            CallRuleKind.ALLOW -> {
                val name = tag.ifBlank { fallbackAllow }
                Result(name, geoLine.ifBlank { name })
            }
            CallRuleKind.LABEL -> {
                val name = tag.ifBlank { number.ifBlank { fallbackLabel } }
                Result(name, geoLine)
            }
            CallRuleKind.BLOCK -> {
                // Lightweight product: treat legacy block as label only; never replace number with geo.
                val mark = tag.ifBlank { fallbackBlock }
                Result(number.ifBlank { spamFmt(mark) }, listOf(mark, geoLine).filter { it.isNotBlank() }.joinToString(" · "))
            }
            null -> {
                if (geoLine.isBlank() && number.isBlank()) null
                else if (geoLine.isBlank()) null
                else Result(
                    displayName = number.ifBlank { geoLine },
                    label = geoLine,
                )
            }
        }
    }
}
