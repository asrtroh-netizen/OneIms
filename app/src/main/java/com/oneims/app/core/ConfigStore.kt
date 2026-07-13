package com.oneims.app.core

import android.annotation.SuppressLint
import android.content.Context
import com.oneims.app.model.WfcMode

/**
 * 轻量配置持久化（SharedPreferences）。
 *
 * 记录「上次成功应用的配置」与「守护开关」，供 [GuardService] / 开机广播在
 * Shizuku 就绪或 IMS 掉线时重应用。只存布尔与少量标量，不含任何敏感信息。
 */
object ConfigStore {

    private const val PREF = "oneims_prefs"
    private const val KEY_GUARD = "guard_enabled"
    private const val KEY_SUB = "last_sub"
    private const val KEY_VOLTE = "last_volte"
    private const val KEY_VOWIFI = "last_vowifi"
    private const val KEY_VONR = "last_vonr"
    private const val KEY_WFC = "last_wfc"
    private const val KEY_HAS = "last_has"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_DYNAMIC_COLOR = "dynamic_color"
    private const val KEY_REAPPLY_TIME = "last_reapply_time"
    private const val KEY_REAPPLY_SUCCESS = "last_reapply_success"
    private const val KEY_REAPPLY_TRIGGER = "last_reapply_trigger"
    private const val KEY_REAPPLY_MESSAGE = "last_reapply_message"
    private const val KEY_ADVANCED_HAS = "advanced_has"
    private const val KEY_ADVANCED_PREFIX = "advanced_"
    private const val KEY_5G_ENABLED = "five_g_display_enabled"
    private const val KEY_5G_MODE = "five_g_display_mode"
    private const val KEY_5G_PLUS_DL = "five_g_plus_dl_threshold"
    private const val KEY_5G_A_DL = "five_g_a_dl_threshold"
    private const val KEY_5G_UL_ENHANCED = "five_g_ul_enhanced_threshold"
    private const val KEY_5G_UL_SUPER = "five_g_ul_super_threshold"
    private const val KEY_5G_SYSTEM_ICON_CONFIG = "five_g_system_icon_config"
    private const val THRESHOLD_MIN = 1
    private const val THRESHOLD_MAX = 10000
    private const val KEY_SIGNAL_STRENGTH_ADJUSTMENT = "signal_strength_adjustment_enabled"
    private const val LEGACY_KEY_SIGNAL_BAR_MODE = "signal_bar_display_mode"
    private const val KEY_SIGNAL_BAR_DISPLAY_MODE = "signal_bar_display_mode_v2"
    private const val KEY_SELECTED_SUB_ID = "selected_sub_id"
    private const val KEY_CAP_UI_HAS = "cap_ui_has"
    private const val KEY_CAP_UI_VOLTE = "cap_ui_volte"
    private const val KEY_CAP_UI_VOWIFI = "cap_ui_vowifi"
    private const val KEY_CAP_UI_VONR = "cap_ui_vonr"
    private const val KEY_CAP_UI_VILTE = "cap_ui_vilte"
    private const val KEY_CAP_UI_UT = "cap_ui_ut"
    private const val KEY_CAP_UI_CROSS_SIM = "cap_ui_cross_sim"
    private const val KEY_CAP_UI_NR5G = "cap_ui_nr5g"
    private const val KEY_CAP_UI_WFC = "cap_ui_wfc"
    private const val KEY_IDENTITY_HAS = "identity_draft_has"
    private const val KEY_IDENTITY_CARRIER = "identity_draft_carrier"
    private const val KEY_IDENTITY_UA = "identity_draft_ua"
    private const val KEY_SIM_COUNTRY_HAS = "sim_country_draft_has"
    private const val KEY_SIM_COUNTRY_ISO = "sim_country_draft_iso"

    data class Applied(
        val subId: Int,
        val volte: Boolean,
        val vowifi: Boolean,
        val vonr: Boolean,
        val wfcMode: WfcMode,
    )

    /**
     * 功能页开关的按卡 UI 快照。切卡只加载该 subId 的快照，绝不把上一张卡的值串过去，
     * 也绝不在切卡时强行清成全关。
     */
    data class CapabilityUiState(
        val volte: Boolean,
        val vowifi: Boolean,
        val vonr: Boolean,
        val vilte: Boolean,
        val ut: Boolean,
        val crossSim: Boolean,
        val nr5g: Boolean,
        val wfcMode: WfcMode,
    )

    data class ReapplyStatus(
        val timestampMillis: Long,
        val success: Boolean,
        val trigger: ReapplyTrigger,
        val message: String,
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun getSelectedSubId(context: Context): Int =
        prefs(context).getInt(KEY_SELECTED_SUB_ID, -1)

    fun setSelectedSubId(context: Context, subId: Int) {
        prefs(context).edit().putInt(KEY_SELECTED_SUB_ID, subId).apply()
    }

    fun capabilityUiState(context: Context, subId: Int): CapabilityUiState? {
        if (subId < 0) return null
        val p = prefs(context)
        if (p.getBoolean("${KEY_CAP_UI_HAS}_$subId", false)) {
            return CapabilityUiState(
                volte = p.getBoolean("${KEY_CAP_UI_VOLTE}_$subId", true),
                vowifi = p.getBoolean("${KEY_CAP_UI_VOWIFI}_$subId", true),
                vonr = p.getBoolean("${KEY_CAP_UI_VONR}_$subId", false),
                vilte = p.getBoolean("${KEY_CAP_UI_VILTE}_$subId", false),
                ut = p.getBoolean("${KEY_CAP_UI_UT}_$subId", false),
                crossSim = p.getBoolean("${KEY_CAP_UI_CROSS_SIM}_$subId", false),
                nr5g = p.getBoolean("${KEY_CAP_UI_NR5G}_$subId", false),
                wfcMode = WfcMode.of(
                    p.getInt("${KEY_CAP_UI_WFC}_$subId", WfcMode.CELLULAR_PREFERRED.value),
                ),
            )
        }
        // 兼容：仅有全局 lastApplied 且 subId 匹配时，迁移为核心三项，避免升级后像被清空。
        val applied = lastApplied(context) ?: return null
        if (applied.subId != subId) return null
        return CapabilityUiState(
            volte = applied.volte,
            vowifi = applied.vowifi,
            vonr = applied.vonr,
            vilte = false,
            ut = false,
            crossSim = false,
            nr5g = false,
            wfcMode = applied.wfcMode,
        )
    }

    @SuppressLint("ApplySharedPref")
    fun setCapabilityUiState(context: Context, subId: Int, state: CapabilityUiState) {
        require(subId >= 0) { "Invalid subscription id: $subId" }
        val saved = prefs(context).edit()
            .putBoolean("${KEY_CAP_UI_HAS}_$subId", true)
            .putBoolean("${KEY_CAP_UI_VOLTE}_$subId", state.volte)
            .putBoolean("${KEY_CAP_UI_VOWIFI}_$subId", state.vowifi)
            .putBoolean("${KEY_CAP_UI_VONR}_$subId", state.vonr)
            .putBoolean("${KEY_CAP_UI_VILTE}_$subId", state.vilte)
            .putBoolean("${KEY_CAP_UI_UT}_$subId", state.ut)
            .putBoolean("${KEY_CAP_UI_CROSS_SIM}_$subId", state.crossSim)
            .putBoolean("${KEY_CAP_UI_NR5G}_$subId", state.nr5g)
            .putInt("${KEY_CAP_UI_WFC}_$subId", state.wfcMode.value)
            .commit()
        check(saved) { "Failed to persist capability UI state for subId=$subId" }
    }

    data class IdentityDraft(
        val carrierName: String,
        val imsUserAgent: String,
    )

    fun identityDraft(context: Context, subId: Int): IdentityDraft? {
        if (subId < 0) return null
        val p = prefs(context)
        if (!p.getBoolean("${KEY_IDENTITY_HAS}_$subId", false)) return null
        return IdentityDraft(
            carrierName = p.getString("${KEY_IDENTITY_CARRIER}_$subId", "").orEmpty(),
            imsUserAgent = p.getString("${KEY_IDENTITY_UA}_$subId", "").orEmpty(),
        )
    }

    @SuppressLint("ApplySharedPref")
    fun setIdentityDraft(context: Context, subId: Int, draft: IdentityDraft) {
        require(subId >= 0) { "Invalid subscription id: $subId" }
        val saved = prefs(context).edit()
            .putBoolean("${KEY_IDENTITY_HAS}_$subId", true)
            .putString("${KEY_IDENTITY_CARRIER}_$subId", draft.carrierName.take(128))
            .putString("${KEY_IDENTITY_UA}_$subId", draft.imsUserAgent.take(256))
            .commit()
        check(saved) { "Failed to persist identity draft for subId=$subId" }
    }

    /** 能力页「SIM 国家码」输入草稿，按 subId 隔离，避免切卡串值。 */
    fun simCountryIsoDraft(context: Context, subId: Int): String? {
        if (subId < 0) return null
        val p = prefs(context)
        if (!p.getBoolean("${KEY_SIM_COUNTRY_HAS}_$subId", false)) return null
        return p.getString("${KEY_SIM_COUNTRY_ISO}_$subId", "").orEmpty()
    }

    @SuppressLint("ApplySharedPref")
    fun setSimCountryIsoDraft(context: Context, subId: Int, iso: String) {
        require(subId >= 0) { "Invalid subscription id: $subId" }
        val saved = prefs(context).edit()
            .putBoolean("${KEY_SIM_COUNTRY_HAS}_$subId", true)
            .putString("${KEY_SIM_COUNTRY_ISO}_$subId", iso.trim().take(8))
            .commit()
        check(saved) { "Failed to persist SIM country ISO draft for subId=$subId" }
    }

    fun isGuardEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_GUARD, false)

    fun setGuardEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_GUARD, enabled).apply()
    }

    /** 0=跟随系统、1=浅色、2=深色；具体枚举由 UI 层解释，避免 core 反向依赖 Compose。 */
    fun themeMode(context: Context): Int =
        prefs(context).getInt(KEY_THEME_MODE, 0)

    fun setThemeMode(context: Context, mode: Int) {
        prefs(context).edit().putInt(KEY_THEME_MODE, mode).apply()
    }

    fun isDynamicColorEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DYNAMIC_COLOR, true)

    fun setDynamicColorEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
    }

    fun saveApplied(context: Context, a: Applied) {
        prefs(context).edit()
            .putInt(KEY_SUB, a.subId)
            .putBoolean(KEY_VOLTE, a.volte)
            .putBoolean(KEY_VOWIFI, a.vowifi)
            .putBoolean(KEY_VONR, a.vonr)
            .putInt(KEY_WFC, a.wfcMode.value)
            .putBoolean(KEY_HAS, true)
            .apply()
    }

    fun lastApplied(context: Context): Applied? {
        val p = prefs(context)
        if (!p.getBoolean(KEY_HAS, false)) return null
        val sub = p.getInt(KEY_SUB, -1)
        if (sub < 0) return null
        return Applied(
            subId = sub,
            volte = p.getBoolean(KEY_VOLTE, true),
            vowifi = p.getBoolean(KEY_VOWIFI, false),
            vonr = p.getBoolean(KEY_VONR, false),
            wfcMode = WfcMode.of(p.getInt(KEY_WFC, WfcMode.CELLULAR_PREFERRED.value)),
        )
    }

    fun saveReapplyStatus(context: Context, status: ReapplyStatus) {
        prefs(context).edit()
            .putLong(KEY_REAPPLY_TIME, status.timestampMillis)
            .putBoolean(KEY_REAPPLY_SUCCESS, status.success)
            .putString(KEY_REAPPLY_TRIGGER, status.trigger.storedValue)
            .putString(KEY_REAPPLY_MESSAGE, status.message.take(512))
            .apply()
    }

    fun lastReapplyStatus(context: Context): ReapplyStatus? {
        val p = prefs(context)
        val timestamp = p.getLong(KEY_REAPPLY_TIME, 0L)
        if (timestamp <= 0L) return null
        return ReapplyStatus(
            timestampMillis = timestamp,
            success = p.getBoolean(KEY_REAPPLY_SUCCESS, false),
            trigger = ReapplyTrigger.fromStored(
                p.getString(KEY_REAPPLY_TRIGGER, null),
            ),
            message = p.getString(KEY_REAPPLY_MESSAGE, "").orEmpty(),
        )
    }

    fun saveAdvancedOptions(context: Context, options: PixelImsOptions) {
        prefs(context).edit()
            .putBoolean(KEY_ADVANCED_HAS, true)
            .putBoolean(KEY_ADVANCED_PREFIX + "wfc_roaming", options.wfcRoamingEnabled)
            .putBoolean(KEY_ADVANCED_PREFIX + "show_wfc_mode", options.showWfcMode)
            .putBoolean(
                KEY_ADVANCED_PREFIX + "show_wfc_roaming_mode",
                options.showWfcRoamingMode,
            )
            .putBoolean(KEY_ADVANCED_PREFIX + "wifi_only", options.supportWifiOnly)
            .putBoolean(KEY_ADVANCED_PREFIX + "allow_apn_add", options.allowAddingApns)
            .putBoolean(KEY_ADVANCED_PREFIX + "vowifi_icon", options.showVowifiIcon)
            .putBoolean(KEY_ADVANCED_PREFIX + "data_rat_icon", options.alwaysShowDataRatIcon)
            .putBoolean(KEY_ADVANCED_PREFIX + "4g_for_lte", options.show4gForLteIcon)
            .putBoolean(KEY_ADVANCED_PREFIX + "hide_lte_plus", options.hideLtePlusIcon)
            .putBoolean(KEY_ADVANCED_PREFIX + "show_ims_status", options.showImsStatus)
            .putBoolean(KEY_ADVANCED_PREFIX + "ss_over_cdma", options.ssOverCdma)
            .putBoolean(KEY_ADVANCED_PREFIX + "enhanced_4g", options.enhanced4g)
            .apply()
    }

    fun lastAdvancedOptions(context: Context): PixelImsOptions? {
        val p = prefs(context)
        if (!p.getBoolean(KEY_ADVANCED_HAS, false)) return null
        return PixelImsOptions(
            wfcRoamingEnabled = p.getBoolean(KEY_ADVANCED_PREFIX + "wfc_roaming", false),
            showWfcMode = p.getBoolean(KEY_ADVANCED_PREFIX + "show_wfc_mode", false),
            showWfcRoamingMode =
                p.getBoolean(KEY_ADVANCED_PREFIX + "show_wfc_roaming_mode", false),
            supportWifiOnly = p.getBoolean(KEY_ADVANCED_PREFIX + "wifi_only", false),
            allowAddingApns = p.getBoolean(KEY_ADVANCED_PREFIX + "allow_apn_add", false),
            showVowifiIcon = p.getBoolean(KEY_ADVANCED_PREFIX + "vowifi_icon", false),
            alwaysShowDataRatIcon =
                p.getBoolean(KEY_ADVANCED_PREFIX + "data_rat_icon", false),
            show4gForLteIcon = p.getBoolean(KEY_ADVANCED_PREFIX + "4g_for_lte", false),
            hideLtePlusIcon = p.getBoolean(KEY_ADVANCED_PREFIX + "hide_lte_plus", false),
            showImsStatus = p.getBoolean(KEY_ADVANCED_PREFIX + "show_ims_status", false),
            ssOverCdma = p.getBoolean(KEY_ADVANCED_PREFIX + "ss_over_cdma", false),
            enhanced4g = p.getBoolean(KEY_ADVANCED_PREFIX + "enhanced_4g", false),
        )
    }

    /**
     * 「5G 显示增强」配置：阈值驱动 App 内展示，系统图标字符串只在用户点击应用后由
     * [SystemDisplayOverrideManager] 写入 selectedSubId。这里仅持久化用户选择，不含敏感信息。
     */
    fun fiveGDisplayConfig(context: Context): SimpleFiveGDisplayConfig {
        val p = prefs(context)
        val default = SimpleFiveGDisplayConfig()
        return SimpleFiveGDisplayConfig(
            enabled = p.getBoolean(KEY_5G_ENABLED, default.enabled),
            mode = p.getString(KEY_5G_MODE, default.mode) ?: default.mode,
            plusDlThresholdMbps = p.getInt(KEY_5G_PLUS_DL, default.plusDlThresholdMbps)
                .coerceIn(THRESHOLD_MIN, THRESHOLD_MAX),
            fiveGaDlThresholdMbps = p.getInt(KEY_5G_A_DL, default.fiveGaDlThresholdMbps)
                .coerceIn(THRESHOLD_MIN, THRESHOLD_MAX),
            uplinkEnhancedThresholdMbps = p.getInt(
                KEY_5G_UL_ENHANCED,
                default.uplinkEnhancedThresholdMbps,
            ).coerceIn(THRESHOLD_MIN, THRESHOLD_MAX),
            superUplinkThresholdMbps = p.getInt(KEY_5G_UL_SUPER, default.superUplinkThresholdMbps)
                .coerceIn(THRESHOLD_MIN, THRESHOLD_MAX),
            systemIconConfigString = p.getString(
                KEY_5G_SYSTEM_ICON_CONFIG,
                default.systemIconConfigString,
            )?.takeIf { it.isNotBlank() } ?: default.systemIconConfigString,
        )
    }

    @SuppressLint("ApplySharedPref")
    fun setFiveGDisplayConfig(context: Context, config: SimpleFiveGDisplayConfig) {
        val saved = prefs(context).edit()
            .putBoolean(KEY_5G_ENABLED, config.enabled)
            .putString(KEY_5G_MODE, config.mode)
            .putInt(KEY_5G_PLUS_DL, config.plusDlThresholdMbps.coerceIn(THRESHOLD_MIN, THRESHOLD_MAX))
            .putInt(KEY_5G_A_DL, config.fiveGaDlThresholdMbps.coerceIn(THRESHOLD_MIN, THRESHOLD_MAX))
            .putInt(
                KEY_5G_UL_ENHANCED,
                config.uplinkEnhancedThresholdMbps.coerceIn(THRESHOLD_MIN, THRESHOLD_MAX),
            )
            .putInt(
                KEY_5G_UL_SUPER,
                config.superUplinkThresholdMbps.coerceIn(THRESHOLD_MIN, THRESHOLD_MAX),
            )
            .putString(
                KEY_5G_SYSTEM_ICON_CONFIG,
                config.systemIconConfigString.trim().take(1024),
            )
            // Apply 是显示策略的提交边界；系统写入前必须确认同一快照已经落盘。
            .commit()
        check(saved) { "Failed to persist 5G display configuration" }
    }

    /**
     * 信号格显示样式（独家页）。AUTO / 四格 / 五格，与能力页「信号阈值」开关彼此独立。
     */
    enum class SignalBarDisplayMode {
        AUTO,
        FOUR_BARS,
        FIVE_BARS;

        companion object {
            fun fromStored(value: String?): SignalBarDisplayMode =
                entries.firstOrNull { mode -> mode.name == value } ?: AUTO
        }
    }

    fun signalBarDisplayMode(context: Context, subId: Int): SignalBarDisplayMode {
        if (subId < 0) return SignalBarDisplayMode.AUTO
        val stored = prefs(context)
        val modeKey = "${KEY_SIGNAL_BAR_DISPLAY_MODE}_$subId"
        stored.getString(modeKey, null)?.let { value ->
            return SignalBarDisplayMode.fromStored(value)
        }
        // 旧版曾用布尔开关兼作样式；仅在样式 key 缺失时迁移一次，不回写布尔。
        val legacyBoolKey = "${KEY_SIGNAL_STRENGTH_ADJUSTMENT}_$subId"
        if (stored.contains(legacyBoolKey)) {
            return if (stored.getBoolean(legacyBoolKey, false)) {
                SignalBarDisplayMode.FIVE_BARS
            } else {
                SignalBarDisplayMode.AUTO
            }
        }
        return if (
            subId == getSelectedSubId(context) &&
            stored.getString(LEGACY_KEY_SIGNAL_BAR_MODE, null) == "FIVE_BARS"
        ) {
            SignalBarDisplayMode.FIVE_BARS
        } else {
            SignalBarDisplayMode.AUTO
        }
    }

    @SuppressLint("ApplySharedPref")
    fun setSignalBarDisplayMode(context: Context, subId: Int, mode: SignalBarDisplayMode) {
        require(subId >= 0) { "Invalid subscription id: $subId" }
        val saved = prefs(context).edit()
            .putString("${KEY_SIGNAL_BAR_DISPLAY_MODE}_$subId", mode.name)
            .remove(LEGACY_KEY_SIGNAL_BAR_MODE)
            .commit()
        check(saved) { "Failed to persist signal-bar display mode" }
    }

    /**
     * 能力页「5G 信号强度/阈值调整」开关。独立存储，不再由独家页格子模式派生。
     */
    fun signalStrengthAdjustmentEnabled(context: Context, subId: Int): Boolean {
        if (subId < 0) return false
        val stored = prefs(context)
        val key = "${KEY_SIGNAL_STRENGTH_ADJUSTMENT}_$subId"
        if (stored.contains(key)) {
            return stored.getBoolean(key, false)
        }
        // 旧版串写：样式非 AUTO 即视为开启阈值；迁移后写入独立布尔，避免继续耦合。
        val legacyEnabled = signalBarDisplayMode(context, subId) != SignalBarDisplayMode.AUTO
        setSignalStrengthAdjustmentEnabled(context, subId, legacyEnabled)
        return legacyEnabled
    }

    @SuppressLint("ApplySharedPref")
    fun setSignalStrengthAdjustmentEnabled(context: Context, subId: Int, enabled: Boolean) {
        require(subId >= 0) { "Invalid subscription id: $subId" }
        val saved = prefs(context).edit()
            .putBoolean("${KEY_SIGNAL_STRENGTH_ADJUSTMENT}_$subId", enabled)
            .commit()
        check(saved) { "Failed to persist signal-strength adjustment preference" }
    }

    /**
     * 一键还原或安全回滚后必须清掉重放源，避免守护服务稍后把用户刚撤销的配置重新写回。
     */
    fun clearAppliedProfiles(context: Context) {
        prefs(context).edit()
            .remove(KEY_HAS)
            .remove(KEY_ADVANCED_HAS)
            .apply()
    }
}
