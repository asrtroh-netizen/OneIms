package com.oneims.app.core

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.oneims.app.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

data class SupportVerifyResult(
    val success: Boolean,
    val message: String,
    val amount: String = "",
    val currency: String = "",
)

data class SupportFeedItem(
    val nickname: String,
    val message: String,
    val amount: String?,
    val timeLabel: String?,
    val authorReply: String?,
)

/**
 * DodoPay 支持作者客户端：本地 client_ref、公开链接构造、proof 解析与公开验证。
 * 全程不持有 API Key；不上传 SIM / subId / 设备标识明文。
 */
object DodoPaySupportClient {
    private const val PREFS = "oneims_prefs"
    private const val KEY_CLIENT_REF = "dodo_support_client_ref"
    private const val KEY_UNLOCKED = "supporter_unlocked"
    private const val KEY_SINCE = "supporter_since"
    private const val KEY_AMOUNT = "supporter_amount"
    private const val KEY_CHANNEL = "supporter_channel"
    private const val KEY_PROOF_MASKED = "supporter_proof_masked"

    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 8_000

    private val PRESET_AMOUNTS = listOf(6, 12, 18, 30, 50)

    fun presetAmounts(): List<Int> = PRESET_AMOUNTS

    fun clientRef(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_CLIENT_REF, null)
        if (!existing.isNullOrBlank()) return existing
        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_CLIENT_REF, created).apply()
        return created
    }

    fun normalizeNickname(raw: String): String {
        val trimmed = raw.trim().replace('\n', ' ').replace('\r', ' ')
        return trimmed.ifBlank { "匿名朋友" }.take(40)
    }

    fun normalizeMessage(raw: String): String {
        val compact = raw.trim()
            .replace("\r\n", " ")
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace(Regex("\\s+"), " ")
        return compact.take(160)
    }

    fun parseAmount(raw: String): Double? {
        val value = raw.trim().replace(',', '.').toDoubleOrNull() ?: return null
        if (value <= 0.0) return null
        if (value > DodoPaySupportConfig.MAX_AMOUNT) return null
        return value
    }

    fun formatAmount(value: Double): String {
        return if (abs(value - value.toLong()) < 1e-9) {
            value.toLong().toString()
        } else {
            String.format(Locale.US, "%.2f", value)
        }
    }

    /**
     * 构造公开支付链接。模板为空返回 null。
     */
    fun buildCheckoutUrl(
        context: Context,
        amount: Double,
        nickname: String,
        message: String,
    ): String? {
        val template = DodoPaySupportConfig.SUPPORT_URL_TEMPLATE.trim()
        if (template.isEmpty()) return null
        // 官方 Dodo Payment Link 价格与结账参数由商户后台固定；追加 CarrierIMS 风格 query 可能干扰结账。
        if (isOfficialDodoCheckout(template)) {
            return template
        }
        val base = Uri.parse(template)
        val builder = base.buildUpon()
        fun put(key: String, value: String) {
            builder.appendQueryParameter(key, value)
        }
        put("amount", formatAmount(amount))
        put("payer_name", normalizeNickname(nickname))
        put("payer_message", normalizeMessage(message))
        put("source", DodoPaySupportConfig.SOURCE)
        put("app_version", BuildConfig.VERSION_NAME)
        put("client_ref", clientRef(context))
        put("channel", DodoPaySupportConfig.CHANNEL)
        put("return_mode", DodoPaySupportConfig.RETURN_MODE)
        put("proof_key", DodoPaySupportConfig.PROOF_KEY)
        put("return_url", DodoPaySupportConfig.CALLBACK_URI)
        return builder.build().toString()
    }

    fun isOfficialDodoCheckout(url: String): Boolean {
        val trimmed = url.trim().lowercase(Locale.US)
        if (trimmed.isEmpty()) return false
        // 纯字符串判断：JVM 单测里 android.net.Uri 是 stub，不能依赖 host 解析。
        return trimmed.contains("://checkout.dodopayments.com/") ||
            trimmed.contains("://test.checkout.dodopayments.com/") ||
            trimmed.contains("://live.checkout.dodopayments.com/")
    }

    fun openCheckout(context: Context, url: String): Boolean {
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    /**
     * 从支付回跳 URL 的 query 或 fragment 提取 `payment_proof`；
     * 只接受 `proof_` 前缀。纯字符串解析，避免 JVM 单测依赖 Android Uri stub。
     */
    fun extractDodopayPaymentProof(url: String): String? {
        if (url.isBlank()) return null
        val queryStart = url.indexOf('?')
        val fragmentStart = url.indexOf('#')
        val query = when {
            queryStart < 0 -> null
            fragmentStart > queryStart -> url.substring(queryStart + 1, fragmentStart)
            else -> url.substring(queryStart + 1)
        }
        val fragment = if (fragmentStart >= 0) url.substring(fragmentStart + 1) else null
        val fromQuery = firstParamValue(query, "payment_proof")
        val fromFragment = firstParamValue(fragment, "payment_proof")
        return listOfNotNull(fromQuery, fromFragment)
            .map { decodeUriComponent(it).trim() }
            .firstOrNull { it.startsWith("proof_") }
    }

    private fun firstParamValue(params: String?, key: String): String? {
        if (params.isNullOrBlank()) return null
        return params.split('&')
            .asSequence()
            .mapNotNull { part ->
                val idx = part.indexOf('=')
                if (idx <= 0) return@mapNotNull null
                val k = part.substring(0, idx)
                val v = part.substring(idx + 1)
                if (k == key) v else null
            }
            .firstOrNull()
    }

    private fun decodeUriComponent(value: String): String =
        runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }.getOrDefault(value)

    fun maskProof(proof: String): String {
        if (proof.length <= 10) return "proof_****"
        return proof.take(8) + "…" + proof.takeLast(4)
    }

    /**
     * 公开验证接口。阻塞调用，请在 IO 线程执行。
     */
    fun verifyDodopayPaymentProof(
        context: Context,
        paymentProof: String,
    ): SupportVerifyResult {
        if (!paymentProof.startsWith("proof_")) {
            return SupportVerifyResult(false, "支付凭证无效")
        }
        if (!DodoPaySupportConfig.isVerifyConfigured()) {
            return SupportVerifyResult(false, "暂未配置支付验证服务")
        }
        val encodedProof = URLEncoder.encode(paymentProof, StandardCharsets.UTF_8.name())
        val base = DodoPaySupportConfig.PROOF_VERIFY_BASE_URL.trim().trimEnd('/')
        val endpoint = "$base/api/public/payment-proofs/$encodedProof"
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", "OneIms-DodoPaySupport/${BuildConfig.VERSION_NAME}")
                setRequestProperty("Accept", "application/json")
            }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            if (code !in 200..299) {
                return SupportVerifyResult(false, "验证服务暂时不可用（$code）")
            }
            parseVerifyResponse(context, body, paymentProof)
        } catch (_: Throwable) {
            SupportVerifyResult(false, "网络异常，请稍后重试验证")
        } finally {
            conn?.disconnect()
        }
    }

    private fun parseVerifyResponse(
        context: Context,
        body: String,
        paymentProof: String,
    ): SupportVerifyResult {
        val obj = runCatching { JSONObject(body) }.getOrNull()
            ?: return SupportVerifyResult(false, "验证响应无法解析")
        val valid = obj.optBoolean("valid", false)
        val status = obj.optString("status")
        val appId = obj.optString("app_id")
        val proofKey = obj.optString("proof_key")
        val remoteClientRef = obj.optString("client_ref")
        val amount = obj.optString("amount").ifBlank {
            obj.optDouble("amount", Double.NaN).takeIf { !it.isNaN() }?.let(::formatAmount).orEmpty()
        }
        val currency = obj.optString("currency").ifBlank { "CNY" }
        val localRef = clientRef(context)
        val amountValue = amount.toDoubleOrNull() ?: obj.optDouble("amount", 0.0)

        when {
            !valid -> return SupportVerifyResult(false, "支付凭证未通过校验")
            !status.equals("paid", ignoreCase = true) ->
                return SupportVerifyResult(false, "尚未确认支付完成")
            appId.isNotBlank() && appId != DodoPaySupportConfig.APP_ID ->
                return SupportVerifyResult(false, "支付凭证不属于 OneIMS")
            proofKey.isNotBlank() && proofKey != DodoPaySupportConfig.PROOF_KEY ->
                return SupportVerifyResult(false, "支付凭证用途不匹配")
            remoteClientRef.isNotBlank() && remoteClientRef != localRef ->
                return SupportVerifyResult(false, "支付凭证与本机不匹配")
            amountValue + 1e-9 < DodoPaySupportConfig.MIN_AMOUNT ->
                return SupportVerifyResult(false, "支持金额未达最低要求")
        }

        persistSupporter(
            context = context,
            amount = amount.ifBlank { formatAmount(amountValue) },
            proofMasked = maskProof(paymentProof),
        )
        return SupportVerifyResult(
            success = true,
            message = "感谢支持 OneIMS",
            amount = amount.ifBlank { formatAmount(amountValue) },
            currency = currency,
        )
    }

    @SuppressLint("ApplySharedPref")
    private fun persistSupporter(context: Context, amount: String, proofMasked: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_UNLOCKED, true)
            .putLong(KEY_SINCE, System.currentTimeMillis())
            .putString(KEY_AMOUNT, amount)
            .putString(KEY_CHANNEL, "dodopay")
            .putString(KEY_PROOF_MASKED, proofMasked)
            .commit()
    }

    fun isSupporterUnlocked(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_UNLOCKED, false)

    fun supporterSince(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_SINCE, 0L)

    fun supporterAmount(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_AMOUNT, "").orEmpty()

    fun supporterChannel(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_CHANNEL, "").orEmpty()

    fun supporterProofMasked(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PROOF_MASKED, "")
            .orEmpty()

    /** 拉取公开留言墙；失败返回空列表（调用方展示错误文案）。阻塞 IO。 */
    fun fetchSupportFeed(): Result<List<SupportFeedItem>> {
        if (!DodoPaySupportConfig.isFeedConfigured()) {
            return Result.success(emptyList())
        }
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(DodoPaySupportConfig.SUPPORT_FEED_URL.trim()).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", "OneIms-DodoPaySupport/${BuildConfig.VERSION_NAME}")
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode !in 200..299) {
                return Result.failure(IllegalStateException("feed http ${conn.responseCode}"))
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            Result.success(parseFeed(text))
        } catch (error: Throwable) {
            Result.failure(error)
        } finally {
            conn?.disconnect()
        }
    }

    private fun parseFeed(text: String): List<SupportFeedItem> {
        val trimmed = text.trim()
        val array = when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            else -> JSONObject(trimmed).optJSONArray("items") ?: JSONArray()
        }
        val items = mutableListOf<SupportFeedItem>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val nickname = obj.optString("nickname").ifBlank { obj.optString("name") }
                .ifBlank { "匿名朋友" }
            val message = obj.optString("message").ifBlank { obj.optString("payer_message") }
            items += SupportFeedItem(
                nickname = nickname,
                message = message,
                amount = obj.optString("amount").takeIf { it.isNotBlank() },
                timeLabel = obj.optString("time").ifBlank { obj.optString("created_at") }
                    .takeIf { it.isNotBlank() },
                authorReply = obj.optString("author_reply").takeIf { it.isNotBlank() },
            )
        }
        return items
    }
}
