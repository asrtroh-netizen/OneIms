package com.onetools.app.caller

import com.onetools.app.BuildConfig
import com.onetools.app.updates.HttpDownloads
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.net.URLEncoder

data class PhoneLocationInfo(
    val province: String = "",
    val city: String = "",
    val cardType: String = "",
) {
    fun dialerLine(): String {
        val area = listOf(province, city).filter { it.isNotBlank() }.distinct().joinToString(" ")
        return listOf(area, cardType).filter { it.isNotBlank() }.joinToString(" · ")
    }
}

data class NetworkQueryResult(
    val isSpam: Boolean,
    val tag: String,
    val source: String,
    val location: PhoneLocationInfo? = null,
    val feedbackToken: String = "",
)

/**
 * Optional cloud query (Telo QueryApi path shape). Disabled when base URL blank.
 */
object CallerNetworkQuery {
    suspend fun query(
        phoneDigits: String,
        timeoutMs: Long,
    ): NetworkQueryResult? = withContext(Dispatchers.IO) {
        val base = BuildConfig.ONE_CALLER_QUERY_URL.trim()
        if (base.isEmpty()) return@withContext null
        val phone = phoneDigits.removePrefix("86")
        val url = if (base.contains("{number}")) {
            base.replace("{number}", URLEncoder.encode(phone, Charsets.UTF_8))
        } else {
            val sep = if (base.contains('?')) '&' else '?'
            "$base${sep}number=${URLEncoder.encode(phone, Charsets.UTF_8)}"
        }
        withTimeout(timeoutMs) {
            val text = HttpDownloads.get(url)
            parse(text)
        }
    }

    fun parse(raw: String): NetworkQueryResult {
        val o = JSONObject(raw)
        val data = o.optJSONObject("data")
        val location = data?.let {
            PhoneLocationInfo(
                province = it.optString("province"),
                city = it.optString("city"),
                cardType = it.optString("cardType").ifBlank { it.optString("card_type") },
            )
        }
        return NetworkQueryResult(
            isSpam = o.optBoolean("is_spam", o.optBoolean("isSpam", false)),
            tag = o.optString("tag"),
            source = o.optString("source"),
            location = location,
            feedbackToken = o.optString("feedback_token"),
        )
    }
}
