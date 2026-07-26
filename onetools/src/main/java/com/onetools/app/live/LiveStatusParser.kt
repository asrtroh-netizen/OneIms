package com.onetools.app.live

/**
 * 从通知标题/正文抽出短状态，供状态栏芯片（约 ≤7 字）使用。
 */
object LiveStatusParser {
    private val noise = Regex(
        "[\\s\\|·•\\-—_【】\\[\\]（）()「」\"'“”]+",
    )

    fun toChipText(source: LiveStatusSource, title: CharSequence?, text: CharSequence?): String {
        val raw = listOfNotNull(title?.toString(), text?.toString())
            .joinToString(" ")
            .replace(noise, "")
            .trim()
        if (raw.isEmpty()) {
            return (source.chipPrefix + "进行中").take(7)
        }
        val body = raw
            .removePrefix(source.chipPrefix)
            .removePrefix("团")
            .removePrefix("滴出行")
            .removePrefix("滴滴")
            .removePrefix("美团")
            .removePrefix("菜鸟")
            .removePrefix("裹裹")
        val compact = body.ifBlank { raw }
        val chip = source.chipPrefix + compact
        return chip.take(7)
    }
}
