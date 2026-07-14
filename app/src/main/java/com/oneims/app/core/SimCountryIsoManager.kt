package com.oneims.app.core

import android.content.Context
import android.os.PersistableBundle
import com.oneims.app.R
import com.oneims.app.model.ConfigResult
import java.util.Locale

/**
 * SIM 国家码（ISO-3166 alpha-2）CarrierConfig 覆盖。
 *
 * 资料要点：
 * - AOSP / CarrierConfig 键：`sim_country_iso_override_string`
 * - 只改展示/上层读取到的国家 ISO，不改基带真实 MCC/MNC，一般不影响通话上网
 * - 常见用途：应用商店/短视频按 SIM 国家分流；空串表示清除覆盖意图（仍受 AOSP putAll 合并语义约束）
 * - TikTok 大陆卡绕过 historically 写 `us`，本 Manager 将其作为预设而非唯一入口
 */
object SimCountryIsoManager {
    private val isoPattern = Regex("^[a-z]{2}$")

    /** 常用预设：ISO → 说明（UI 展示用）。 */
    val presets: List<Pair<String, Int>> = listOf(
        "us" to R.string.sim_country_preset_us,
        "jp" to R.string.sim_country_preset_jp,
        "hk" to R.string.sim_country_preset_hk,
        "tw" to R.string.sim_country_preset_tw,
        "gb" to R.string.sim_country_preset_gb,
        "kr" to R.string.sim_country_preset_kr,
        "cn" to R.string.sim_country_preset_cn,
        "sg" to R.string.sim_country_preset_sg,
    )

    fun normalize(raw: String): String =
        raw.trim().lowercase(Locale.ROOT)

    fun requireValidIso(raw: String): String {
        val iso = normalize(raw)
        require(isoPattern.matches(iso)) {
            "SIM country ISO must be 2 letters, got: $raw"
        }
        return iso
    }

    fun apply(context: Context, subId: Int, rawIso: String): ConfigResult {
        require(subId >= 0) { "Invalid subscription id: $subId" }
        return try {
            val iso = requireValidIso(rawIso)
            val bundle = PersistableBundle().apply {
                putString(CarrierConfigKeys.SIM_COUNTRY_ISO_OVERRIDE, iso)
            }
            run {
                val write = CarrierConfigOverrideWriter.applyPersistentOverride(
                    context, subId, bundle, reason = "SimCountryIso",
                )
                check(write.success) { write.message }
            }
            val readback = readCurrent(context, subId)
            if (readback != null && !readback.equals(iso, ignoreCase = true)) {
                return ConfigResult(
                    false,
                    context.getString(
                        R.string.msg_sim_country_readback_mismatch,
                        iso,
                        readback,
                    ),
                )
            }
            ConfigResult(true, context.getString(R.string.msg_sim_country_applied, iso.uppercase(Locale.ROOT)))
        } catch (e: Throwable) {
            ConfigResult(
                false,
                context.getString(R.string.msg_write_failed, OperationErrors.describe(e)),
            )
        }
    }

    /** 写入空串，表达「清除覆盖」意图。 */
    fun clear(context: Context, subId: Int): ConfigResult {
        require(subId >= 0) { "Invalid subscription id: $subId" }
        return try {
            val bundle = PersistableBundle().apply {
                putString(CarrierConfigKeys.SIM_COUNTRY_ISO_OVERRIDE, "")
            }
            run {
                val write = CarrierConfigOverrideWriter.applyPersistentOverride(
                    context, subId, bundle, reason = "SimCountryIso",
                )
                check(write.success) { write.message }
            }
            ConfigResult(true, context.getString(R.string.msg_sim_country_cleared))
        } catch (e: Throwable) {
            ConfigResult(
                false,
                context.getString(R.string.msg_write_failed, OperationErrors.describe(e)),
            )
        }
    }

    fun readCurrent(context: Context, subId: Int): String? {
        if (subId < 0) return null
        val config = SystemApiBroker.getCarrierConfig(context, subId) ?: return null
        if (!config.containsKey(CarrierConfigKeys.SIM_COUNTRY_ISO_OVERRIDE)) return null
        return config.getString(CarrierConfigKeys.SIM_COUNTRY_ISO_OVERRIDE)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    /** TikTok 常用预设：覆盖为 us。 */
    fun applyTikTokPreset(context: Context, subId: Int): ConfigResult =
        apply(context, subId, "us")
}
