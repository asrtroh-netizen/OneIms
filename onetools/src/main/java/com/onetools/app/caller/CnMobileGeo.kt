package com.onetools.app.caller

import android.content.Context
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

/**
 * Clean-room CN mobile geo for native dialer labels.
 * Data: assets/caller/geo_v1.json (OneTools schema) — not GPL phone.dat.
 */
object CnMobileGeo {
    private const val ASSET = "caller/geo_v1.json"

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

    private val mapRef = AtomicReference<Map<String, Hit>?>()

    fun warm(context: Context) {
        load(context.applicationContext)
    }

    fun lookup(context: Context, rawDigits: String): Hit? {
        val map = load(context.applicationContext) ?: return null
        val phone = normalizeMobile(rawDigits) ?: return null
        // Longest prefix first: 7 then 6 then 5 (data uses 7-digit keys primarily).
        for (len in 7 downTo 5) {
            if (phone.length >= len) {
                map[phone.substring(0, len)]?.let { return it }
            }
        }
        return null
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

    private fun load(context: Context): Map<String, Hit>? {
        mapRef.get()?.let { return it }
        return runCatching {
            context.assets.open(ASSET).bufferedReader().use { it.readText() }
                .let { parseJson(it) }
                .also { mapRef.set(it) }
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
