package com.onetools.app.special.display

import android.content.Context
import android.os.PersistableBundle
import com.onetools.app.special.broker.SpecialBroker
import com.onetools.app.special.broker.SpecialErrors

/**
 * 5G 显示增强：写入 `5g_icon_configuration_string`（对齐 OneIMS SystemDisplayOverrideManager 核心路径）。
 */
object FiveGDisplayController {
    private const val CONSERVATIVE =
        "connected_mmwave:5G_PLUS,connected:5G,not_restricted_rrc_idle:5G"
    private const val COOL =
        "connected_mmwave:5G_PLUS,connected:5G_PLUS,not_restricted_rrc_idle:5G_PLUS"

    fun iconStringFor(config: SpecialFeatureStore.FiveGConfig): String = when (config.mode) {
        SpecialFeatureStore.FiveGConfig.Mode.CONSERVATIVE -> CONSERVATIVE
        SpecialFeatureStore.FiveGConfig.Mode.CN_SPEED ->
            SpecialFeatureStore.FiveGConfig.DEFAULT_SYSTEM_ICON_CONFIG
        SpecialFeatureStore.FiveGConfig.Mode.COOL -> COOL
        SpecialFeatureStore.FiveGConfig.Mode.CUSTOM -> {
            val normalized = config.systemIconConfigString.trim()
            require(normalized.isNotEmpty()) { "自定义 5G 图标配置不能为空" }
            normalized
        }
        else -> error("Unsupported 5G mode: ${config.mode}")
    }

    fun apply(context: Context, subId: Int, config: SpecialFeatureStore.FiveGConfig): String {
        require(subId >= 0) { "Invalid subscription id" }
        val committed = if (
            config.enabled &&
            config.mode == SpecialFeatureStore.FiveGConfig.Mode.CUSTOM
        ) {
            config.copy(systemIconConfigString = iconStringFor(config))
        } else {
            config
        }
        SpecialFeatureStore.setFiveGConfig(context, committed)
        SpecialFeatureStore.setLastFiveGSubId(context, subId)
        return runCatching {
            if (!committed.enabled) {
                val baseline = SpecialFeatureStore.fiveGBaseline(context, subId)
                if (baseline != null) {
                    writeIcon(context, subId, baseline)
                    "已恢复系统 5G 图标配置"
                } else {
                    "已保存本地关闭；当前没有可恢复的系统覆盖"
                }
            } else {
                captureBaselineOnce(context, subId)
                val icon = iconStringFor(committed)
                writeIcon(context, subId, icon)
                check(readIcon(context, subId) == icon) { "5G icon readback mismatch" }
                "已应用 5G 显示增强到系统"
            }
        }.getOrElse { error ->
            throw IllegalStateException(
                "5G 显示增强应用失败：${SpecialErrors.describe(error)}",
                error,
            )
        }
    }

    private fun captureBaselineOnce(context: Context, subId: Int) {
        if (SpecialFeatureStore.fiveGBaseline(context, subId) != null) return
        val current = readIcon(context, subId) ?: return
        SpecialFeatureStore.setFiveGBaseline(context, subId, current)
    }

    private fun writeIcon(context: Context, subId: Int, value: String) {
        val overrides = PersistableBundle().apply {
            putString(DisplayKeys.FIVE_G_ICON_CONFIGURATION_STRING, value)
        }
        SpecialBroker.overrideConfigBestEffort(context, subId, overrides)
    }

    private fun readIcon(context: Context, subId: Int): String? {
        val bundle = SpecialBroker.getCarrierConfig(context, subId) ?: return null
        return bundle.getString(DisplayKeys.FIVE_G_ICON_CONFIGURATION_STRING)
    }
}
