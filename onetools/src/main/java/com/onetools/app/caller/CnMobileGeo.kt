package com.onetools.app.caller

import android.content.Context
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

/**
 * CN mobile geo for native dialer labels.
 *
 * Primary: assets/caller/geo.dat (full prefix index, MIT dataset — see NOTICE)
 * Fallback: assets/caller/geo_v1.json (small starter / override)
 */
object CnMobileGeo {
    private const val ASSET_DAT = "caller/geo.dat"
    private const val ASSET_JSON = "caller/geo_v1.json"

    data class Hit(
        val province: String,
        val city: String,
        val carrier: String,
    ) {
        fun dialerLine(): String {
            val place = when {
                province.isNotBlank() && city.isNotBlank() && province != city -> "$province$city"
                city.isNotBlank() -> city
                else -> province
            }
            val op = shortCarrier(carrier)
            return when {
                place.isNotBlank() && op.isNotBlank() -> "$place · $op"
                place.isNotBlank() -> place
                else -> op
            }
        }
    }

    private val datRef = AtomicReference<ByteArray?>()
    private val jsonRef = AtomicReference<Map<String, Hit>?>()
    private val versionRef = AtomicReference<String?>()

    fun warm(context: Context) {
        loadDat(context.applicationContext)
        loadJson(context.applicationContext)
    }

    fun dataVersion(): String? = versionRef.get()

    fun lookup(context: Context, rawDigits: String): Hit? {
        val phone = normalizeMobile(rawDigits) ?: return null
        val dat = loadDat(context.applicationContext)
        if (dat != null) {
            PhoneDatIndex.lookup(dat, phone)?.let { return it }
        }
        val map = loadJson(context.applicationContext) ?: return null
        return lookupMap(map, phone)
    }

    internal fun lookupMap(map: Map<String, Hit>, rawDigits: String): Hit? {
        val phone = normalizeMobile(rawDigits) ?: return null
        for (len in 7 downTo 5) {
            if (phone.length >= len) {
                map[phone.substring(0, len)]?.let { return it }
            }
        }
        return null
    }

    internal fun parseJson(raw: String): Map<String, Hit> {
        val root = JSONObject(raw)
        require(root.optString("schema") == "onetools.geo.v1") { "need onetools.geo.v1" }
        val prefixes = root.getJSONObject("prefixes")
        val out = HashMap<String, Hit>(prefixes.length())
        val keys = prefixes.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val o = prefixes.getJSONObject(k)
            out[k] = Hit(
                province = o.optString("p"),
                city = o.optString("c"),
                carrier = o.optString("op"),
            )
        }
        return out
    }

    private fun loadDat(context: Context): ByteArray? {
        datRef.get()?.let { return it }
        return runCatching {
            context.assets.open(ASSET_DAT).use { it.readBytes() }.also { bytes ->
                datRef.set(bytes)
                versionRef.set(PhoneDatIndex.versionLabel(bytes))
            }
        }.getOrNull()
    }

    private fun loadJson(context: Context): Map<String, Hit>? {
        jsonRef.get()?.let { return it }
        return runCatching {
            context.assets.open(ASSET_JSON).bufferedReader().use { it.readText() }
                .let { parseJson(it) }
                .also { jsonRef.set(it) }
        }.getOrNull()
    }

    private fun normalizeMobile(raw: String): String? {
        var d = NumberMatcher.digits(raw)
        when {
            d.startsWith("86") && d.length >= 13 -> d = d.substring(2)
            d.startsWith("0086") && d.length >= 15 -> d = d.substring(4)
        }
        if (d.length >= 11 && d[0] == '1') return d.substring(0, 11)
        return null
    }

    private fun shortCarrier(full: String): String = when {
        full.contains("移动") -> "移动"
        full.contains("联通") -> "联通"
        full.contains("电信") -> "电信"
        full.contains("广电") -> "广电"
        else -> full.removePrefix("中国")
    }
}
