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
    private const val KEY_ADVANCED_SUB_ID = "advanced_sub_id"
    private const val KEY_ADVANCED_PREFIX = "advanced_"
    /** 按 subId 隔离后的 has 键前缀：`advanced_has_<subId>`。 */
    private const val KEY_ADVANCED_HAS_PREFIX = "advanced_has_"
    private val ADVANCED_OPTION_FIELDS = listOf(
        "wfc_roaming",
        "show_wfc_mode",
        "show_wfc_roaming_mode",
        "wifi_only",
        "allow_apn_add",
        "vowifi_icon",
        "data_rat_icon",
        "4g_for_lte",
        "hide_lte_plus",
        "show_ims_status",
        "ss_over_cdma",
        "enhanced_4g",
    )
    private const val KEY_5G_ENABLED = "five_g_display_enabled"
    private const val KEY_5G_LAST_SUB_ID = "five_g_display_last_sub_id"
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
    private const val KEY_ONEKUKU_AUTO_SLEEP = "onekuku_auto_sleep"
    private const val KEY_ONEKUKU_AUTO_RESTORE = "onekuku_auto_restore"
    /** 与守护解耦后的「开机自动检查」；无键时默认开，旧数据回退 guard。 */
    private const val KEY_ONEKUKU_BOOT_AUTO_CHECK = "onekuku_boot_auto_check"
    /** Root 持久化增强开关；默认关，免 Root 路径零感知。 */
    private const val KEY_ROOT_PERSIST_ENHANCE = "root_persist_enhance"
    private const val KEY_LAST_OVERRIDE_PERSISTENT = "last_override_persistent"
    private const val KEY_LAST_OVERRIDE_PERSIST_HAS = "last_override_persist_has"
    private const val KEY_LAST_OVERRIDE_SUCCESS = "last_override_success"
    private const val KEY_LAST_OVERRIDE_AT = "last_override_at"

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

    /**
     * 按 subId 持久化高级选项（双卡各一份，互不覆盖）。
     * 同时更新 [KEY_ADVANCED_SUB_ID] 为「最近一次成功应用」的卡，供诊断展示。
     */
    fun saveAdvancedOptions(context: Context, options: PixelImsOptions, subId: Int) {
        require(subId >= 0) { "Invalid subscription id for advanced options: $subId" }
        migrateLegacyAdvancedIfNeeded(context)
        prefs(context).edit()
            .putBoolean(advancedHasKey(subId), true)
            .putInt(KEY_ADVANCED_SUB_ID, subId)
            .putBoolean(advancedFieldKey("wfc_roaming", subId), options.wfcRoamingEnabled)
            .putBoolean(advancedFieldKey("show_wfc_mode", subId), options.showWfcMode)
            .putBoolean(
                advancedFieldKey("show_wfc_roaming_mode", subId),
                options.showWfcRoamingMode,
            )
            .putBoolean(advancedFieldKey("wifi_only", subId), options.supportWifiOnly)
            .putBoolean(advancedFieldKey("allow_apn_add", subId), options.allowAddingApns)
            .putBoolean(advancedFieldKey("vowifi_icon", subId), options.showVowifiIcon)
            .putBoolean(advancedFieldKey("data_rat_icon", subId), options.alwaysShowDataRatIcon)
            .putBoolean(advancedFieldKey("4g_for_lte", subId), options.show4gForLteIcon)
            .putBoolean(advancedFieldKey("hide_lte_plus", subId), options.hideLtePlusIcon)
            .putBoolean(advancedFieldKey("show_ims_status", subId), options.showImsStatus)
            .putBoolean(advancedFieldKey("ss_over_cdma", subId), options.ssOverCdma)
            .putBoolean(advancedFieldKey("enhanced_4g", subId), options.enhanced4g)
            .apply()
    }

    /** 是否存在任意一张卡的高级选项重放源（含未迁移的旧全局键）。 */
    fun hasAnyAdvancedOptions(context: Context): Boolean {
        migrateLegacyAdvancedIfNeeded(context)
        return listAdvancedOptionSubIds(context).isNotEmpty()
    }

    /** 所有已持久化高级选项的 subId（升序，稳定重放顺序）。 */
    fun listAdvancedOptionSubIds(context: Context): List<Int> {
        migrateLegacyAdvancedIfNeeded(context)
        val p = prefs(context)
        val prefix = "${KEY_ADVANCED_HAS}_"
        return p.all.keys
            .mapNotNull { key ->
                if (!key.startsWith(prefix)) return@mapNotNull null
                key.removePrefix(prefix).toIntOrNull()?.takeIf { it >= 0 }
            }
            .distinct()
            .sorted()
    }

    fun lastAdvancedOptions(context: Context, subId: Int): PixelImsOptions? {
        if (subId < 0) return null
        migrateLegacyAdvancedIfNeeded(context)
        val p = prefs(context)
        if (!p.getBoolean(advancedHasKey(subId), false)) return null
        return PixelImsOptions(
            wfcRoamingEnabled = p.getBoolean(advancedFieldKey("wfc_roaming", subId), false),
            showWfcMode = p.getBoolean(advancedFieldKey("show_wfc_mode", subId), false),
            showWfcRoamingMode =
                p.getBoolean(advancedFieldKey("show_wfc_roaming_mode", subId), false),
            supportWifiOnly = p.getBoolean(advancedFieldKey("wifi_only", subId), false),
            allowAddingApns = p.getBoolean(advancedFieldKey("allow_apn_add", subId), false),
            showVowifiIcon = p.getBoolean(advancedFieldKey("vowifi_icon", subId), false),
            alwaysShowDataRatIcon =
                p.getBoolean(advancedFieldKey("data_rat_icon", subId), false),
            show4gForLteIcon = p.getBoolean(advancedFieldKey("4g_for_lte", subId), false),
            hideLtePlusIcon = p.getBoolean(advancedFieldKey("hide_lte_plus", subId), false),
            showImsStatus = p.getBoolean(advancedFieldKey("show_ims_status", subId), false),
            ssOverCdma = p.getBoolean(advancedFieldKey("ss_over_cdma", subId), false),
            enhanced4g = p.getBoolean(advancedFieldKey("enhanced_4g", subId), false),
        )
    }

    /**
     * 最近一次成功「应用高级选项」的 subId（诊断用）；无任何记录时返回 -1。
     * 开机重放请用 [listAdvancedOptionSubIds]，勿再依赖本单值。
     */
    fun lastAdvancedOptionsSubId(context: Context): Int {
        migrateLegacyAdvancedIfNeeded(context)
        val ids = listAdvancedOptionSubIds(context)
        if (ids.isEmpty()) return -1
        val p = prefs(context)
        if (p.contains(KEY_ADVANCED_SUB_ID)) {
            val last = p.getInt(KEY_ADVANCED_SUB_ID, -1)
            if (last in ids) return last
        }
        return ids.last()
    }

    private fun advancedHasKey(subId: Int): String = "${KEY_ADVANCED_HAS}_$subId"

    private fun advancedFieldKey(field: String, subId: Int): String =
        "${KEY_ADVANCED_PREFIX}${field}_$subId"

    /** 旧版全局单槽 advanced_* → 迁到 advanced_*_$subId，避免升级后双卡仍只剩一张。 */
    private fun migrateLegacyAdvancedIfNeeded(context: Context) {
        val p = prefs(context)
        if (!p.getBoolean(KEY_ADVANCED_HAS, false)) return
        val legacySubId = when {
            p.contains(KEY_ADVANCED_SUB_ID) -> p.getInt(KEY_ADVANCED_SUB_ID, -1)
            else -> lastApplied(context)?.subId?.takeIf { it >= 0 }
                ?: getSelectedSubId(context).takeIf { it >= 0 }
                ?: -1
        }
        if (legacySubId < 0) {
            p.edit().remove(KEY_ADVANCED_HAS).apply()
            return
        }
        if (p.getBoolean(advancedHasKey(legacySubId), false)) {
            clearLegacyAdvancedKeys(context)
            return
        }
        val options = PixelImsOptions(
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
        // 直接写 per-sub，避免 save→migrate 递归；再清旧键。
        prefs(context).edit()
            .putBoolean(advancedHasKey(legacySubId), true)
            .putInt(KEY_ADVANCED_SUB_ID, legacySubId)
            .putBoolean(advancedFieldKey("wfc_roaming", legacySubId), options.wfcRoamingEnabled)
            .putBoolean(advancedFieldKey("show_wfc_mode", legacySubId), options.showWfcMode)
            .putBoolean(
                advancedFieldKey("show_wfc_roaming_mode", legacySubId),
                options.showWfcRoamingMode,
            )
            .putBoolean(advancedFieldKey("wifi_only", legacySubId), options.supportWifiOnly)
            .putBoolean(advancedFieldKey("allow_apn_add", legacySubId), options.allowAddingApns)
            .putBoolean(advancedFieldKey("vowifi_icon", legacySubId), options.showVowifiIcon)
            .putBoolean(
                advancedFieldKey("data_rat_icon", legacySubId),
                options.alwaysShowDataRatIcon,
            )
            .putBoolean(advancedFieldKey("4g_for_lte", legacySubId), options.show4gForLteIcon)
            .putBoolean(advancedFieldKey("hide_lte_plus", legacySubId), options.hideLtePlusIcon)
            .putBoolean(advancedFieldKey("show_ims_status", legacySubId), options.showImsStatus)
            .putBoolean(advancedFieldKey("ss_over_cdma", legacySubId), options.ssOverCdma)
            .putBoolean(advancedFieldKey("enhanced_4g", legacySubId), options.enhanced4g)
            .apply()
        clearLegacyAdvancedKeys(context)
    }

    private fun clearLegacyAdvancedKeys(context: Context) {
        prefs(context).edit()
            .remove(KEY_ADVANCED_HAS)
            .remove(KEY_ADVANCED_PREFIX + "wfc_roaming")
            .remove(KEY_ADVANCED_PREFIX + "show_wfc_mode")
            .remove(KEY_ADVANCED_PREFIX + "show_wfc_roaming_mode")
            .remove(KEY_ADVANCED_PREFIX + "wifi_only")
            .remove(KEY_ADVANCED_PREFIX + "allow_apn_add")
            .remove(KEY_ADVANCED_PREFIX + "vowifi_icon")
            .remove(KEY_ADVANCED_PREFIX + "data_rat_icon")
            .remove(KEY_ADVANCED_PREFIX + "4g_for_lte")
            .remove(KEY_ADVANCED_PREFIX + "hide_lte_plus")
            .remove(KEY_ADVANCED_PREFIX + "show_ims_status")
            .remove(KEY_ADVANCED_PREFIX + "ss_over_cdma")
            .remove(KEY_ADVANCED_PREFIX + "enhanced_4g")
            .apply()
    }

    private fun clearAllAdvancedOptions(context: Context) {
        val p = prefs(context)
        val editor = p.edit()
        for (key in p.all.keys) {
            if (key == KEY_ADVANCED_HAS ||
                key == KEY_ADVANCED_SUB_ID ||
                key.startsWith("${KEY_ADVANCED_HAS}_") ||
                key.startsWith(KEY_ADVANCED_PREFIX)
            ) {
                editor.remove(key)
            }
        }
        editor.apply()
    }

    /** 上次成功写入系统 5G 显示覆盖的目标 subId；无记录时返回 -1。 */
    fun lastFiveGDisplaySubId(context: Context): Int =
        prefs(context).getInt(KEY_5G_LAST_SUB_ID, -1)

    fun setLastFiveGDisplaySubId(context: Context, subId: Int) {
        require(subId >= 0) { "Invalid subscription id for 5G display: $subId" }
        prefs(context).edit().putInt(KEY_5G_LAST_SUB_ID, subId).apply()
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
        // 仅认真正的旧版全局样式键。绝不读取 KEY_SIGNAL_STRENGTH_ADJUSTMENT：
        // 该布尔是能力页「信号阈值」现行偏好，再当格子迁移源会把「只开阈值」误读成五格。
        val legacyMode = if (
            subId == getSelectedSubId(context) &&
            stored.getString(LEGACY_KEY_SIGNAL_BAR_MODE, null) == "FIVE_BARS"
        ) {
            SignalBarDisplayMode.FIVE_BARS
        } else {
            SignalBarDisplayMode.AUTO
        }
        // 落盘 v2，避免下次再走 legacy；不触碰阈值布尔。
        setSignalBarDisplayMode(context, subId, legacyMode)
        return legacyMode
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
     * 能力页「5G 信号强度/阈值调整」开关。独立存储，与独家页格子模式互不派生。
     */
    fun signalStrengthAdjustmentEnabled(context: Context, subId: Int): Boolean {
        if (subId < 0) return false
        val stored = prefs(context)
        val key = "${KEY_SIGNAL_STRENGTH_ADJUSTMENT}_$subId"
        if (stored.contains(key)) {
            return stored.getBoolean(key, false)
        }
        // 缺省 false，并立刻落盘封印；不再用格子模式反推，否则独家页选五格会串开能力页阈值。
        setSignalStrengthAdjustmentEnabled(context, subId, false)
        return false
    }

    @SuppressLint("ApplySharedPref")
    fun setSignalStrengthAdjustmentEnabled(context: Context, subId: Int, enabled: Boolean) {
        require(subId >= 0) { "Invalid subscription id: $subId" }
        val editor = prefs(context).edit()
            .putBoolean("${KEY_SIGNAL_STRENGTH_ADJUSTMENT}_$subId", enabled)
        // 若格子 v2 尚未落盘，显式写成 AUTO，杜绝任何「缺 key 回落」路径再碰阈值布尔。
        val modeKey = "${KEY_SIGNAL_BAR_DISPLAY_MODE}_$subId"
        if (!prefs(context).contains(modeKey)) {
            editor.putString(modeKey, SignalBarDisplayMode.AUTO.name)
            editor.remove(LEGACY_KEY_SIGNAL_BAR_MODE)
        }
        val saved = editor.commit()
        check(saved) { "Failed to persist signal-strength adjustment preference" }
    }

    /**
     * 一键还原或安全回滚后必须清掉重放源，避免守护服务稍后把用户刚撤销的配置重新写回。
     */
    fun clearAppliedProfiles(context: Context) {
        prefs(context).edit()
            .remove(KEY_HAS)
            .remove(KEY_5G_LAST_SUB_ID)
            .apply()
        clearAllAdvancedOptions(context)
    }

    /**
     * OneKuku「开机自动检查」：独立键，默认开启。
     * 写入时同步守护开关，便于 [BootReceiver] 仍拉起 GuardService。
     */
    fun isOneKukuBootAutoCheck(context: Context): Boolean {
        val p = prefs(context)
        if (p.contains(KEY_ONEKUKU_BOOT_AUTO_CHECK)) {
            return p.getBoolean(KEY_ONEKUKU_BOOT_AUTO_CHECK, true)
        }
        // 旧安装：跟守护；全新无任何键 → 默认开
        if (p.contains(KEY_GUARD)) return p.getBoolean(KEY_GUARD, false)
        return true
    }

    fun setOneKukuBootAutoCheck(context: Context, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_ONEKUKU_BOOT_AUTO_CHECK, enabled)
            .putBoolean(KEY_GUARD, enabled)
            .apply()
    }

    /** OneKuku「用完自动休眠」偏好；默认开启（降发热；唤醒走秒级 binder）。 */
    fun isOneKukuAutoSleep(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ONEKUKU_AUTO_SLEEP, true)

    fun setOneKukuAutoSleep(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ONEKUKU_AUTO_SLEEP, enabled).apply()
    }

    /** 检测到配置失效时是否自动唤醒恢复；默认开启。 */
    fun isOneKukuAutoRestore(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ONEKUKU_AUTO_RESTORE, true)

    fun setOneKukuAutoRestore(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ONEKUKU_AUTO_RESTORE, enabled).apply()
    }

    /** Root 用户旁路：是否启用持久化增强展示/文案；默认关闭。 */
    fun isRootPersistEnhance(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ROOT_PERSIST_ENHANCE, false)

    fun setRootPersistEnhance(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ROOT_PERSIST_ENHANCE, enabled).apply()
    }

    data class OverridePersistMode(
        val persistent: Boolean,
        val success: Boolean,
        val atMillis: Long,
    )

    fun lastOverridePersistMode(context: Context): OverridePersistMode? {
        val p = prefs(context)
        if (!p.getBoolean(KEY_LAST_OVERRIDE_PERSIST_HAS, false)) return null
        return OverridePersistMode(
            persistent = p.getBoolean(KEY_LAST_OVERRIDE_PERSISTENT, false),
            success = p.getBoolean(KEY_LAST_OVERRIDE_SUCCESS, false),
            atMillis = p.getLong(KEY_LAST_OVERRIDE_AT, 0L),
        )
    }

    fun setLastOverridePersistMode(
        context: Context,
        persistent: Boolean,
        success: Boolean,
    ) {
        prefs(context).edit()
            .putBoolean(KEY_LAST_OVERRIDE_PERSIST_HAS, true)
            .putBoolean(KEY_LAST_OVERRIDE_PERSISTENT, persistent)
            .putBoolean(KEY_LAST_OVERRIDE_SUCCESS, success)
            .putLong(KEY_LAST_OVERRIDE_AT, System.currentTimeMillis())
            .apply()
    }
}
