package com.oneims.app.core

/**
 * 强开 IMS 能力所需的 CarrierConfig 键集合。
 *
 * 直接用 AOSP KEY 的字面值而非引用 hidden 字段，避免依赖非公开 SDK 即可编译。
 * 只负责让系统「露出开关 + 判定可用」，真正持久化启用见 ProvisioningKeys。
 *
 * ⚠️ OneIms 安全铁律：IMS 能力键保持「加法」语义；显示层键只由专用管理器写入并回读。
 * 绝不包含任何会改「首选网络类型 / 禁用蜂窝 / 强制 RAT」的键，以免搞挂电话/数据/短信。
 */
object CarrierConfigKeys {
    const val VOLTE_AVAILABLE = "carrier_volte_available_bool"
    const val VT_AVAILABLE = "carrier_vt_available_bool"
    const val ENHANCED_4G_LTE_ON_BY_DEFAULT = "enhanced_4g_lte_on_by_default_bool"
    const val EDITABLE_ENHANCED_4G_LTE = "editable_enhanced_4g_lte_bool"
    const val HIDE_ENHANCED_4G_LTE = "hide_enhanced_4g_lte_bool"

    const val WFC_IMS_AVAILABLE = "carrier_wfc_ims_available_bool"
    const val WFC_SUPPORTS_WIFI_ONLY = "carrier_wfc_supports_wifi_only_bool"
    const val WFC_DEFAULT_ROAMING_ENABLED = "carrier_default_wfc_ims_roaming_enabled_bool"
    const val EDITABLE_WFC_MODE = "editable_wfc_mode_bool"
    const val EDITABLE_WFC_ROAMING_MODE = "editable_wfc_roaming_mode_bool"
    const val WFC_SPN_FORMAT_IDX = "wfc_spn_format_idx_int"
    const val WFC_DATA_SPN_FORMAT_IDX = "wfc_data_spn_format_idx_int"
    const val WFC_FLIGHT_MODE_SPN_FORMAT_IDX = "wfc_flight_mode_spn_format_idx_int"
    const val WFC_SPN_USE_ROOT_LOCALE = "wfc_spn_use_root_locale"

    const val VONR_ENABLED = "vonr_enabled_bool"
    const val VONR_SETTING_VISIBILITY = "vonr_setting_visibility_bool"
    /** 用户偏好默认开 VoNR（有设置项时）。 */
    const val VONR_ON_BY_DEFAULT = "vonr_on_by_default_bool"
    /** AOSP：NSA=1 / SA=2；Settings 要求该数组非空才露出 VoNR 菜单。 */
    val NR_AVAILABILITIES_NSA_AND_SA = intArrayOf(1, 2)

    // UT（补充业务：呼叫转移/等待等）
    const val CARRIER_SUPPORTS_SS_OVER_UT = "carrier_supports_ss_over_ut_bool"
    const val CARRIER_UT_PROVISIONING_REQUIRED = "carrier_ut_provisioning_required_bool"
    const val SUPPORT_SS_OVER_CDMA = "support_ss_over_cdma_bool"

    // Cross-SIM（双卡互通）
    const val CROSS_SIM_IMS_AVAILABLE = "carrier_cross_sim_ims_available_bool"
    const val ENABLE_CROSS_SIM_CALLING_ON_OPPORTUNISTIC_DATA =
        "enable_cross_sim_calling_on_opportunistic_data_bool"

    // 5G NR 可用性（[1,2] = NSA + SA）与信号阈值
    const val NR_AVAILABILITIES_INT_ARRAY = "carrier_nr_availabilities_int_array"
    const val FIVE_G_ICON_CONFIGURATION_STRING = "5g_icon_configuration_string"
    /** 聚合带宽达到该阈值(kHz)时，Telephony 上报 NR Advanced；国行 ROM 常据此切 5G-A 图标。 */
    const val NR_ADVANCED_THRESHOLD_BANDWIDTH_KHZ =
        "nr_advanced_threshold_bandwidth_khz_int"
    const val INCLUDE_LTE_FOR_NR_ADVANCED_THRESHOLD =
        "include_lte_for_nr_advanced_threshold_bandwidth_bool"
    const val INFLATE_SIGNAL_STRENGTH_BOOL = "inflate_signal_strength_bool"
    const val NR_SSRSRP_THRESHOLDS_INT_ARRAY = "5g_nr_ssrsrp_thresholds_int_array"
    const val NR_SSRSRQ_THRESHOLDS_INT_ARRAY = "5g_nr_ssrsrq_thresholds_int_array"
    const val NR_SSSINR_THRESHOLDS_INT_ARRAY = "5g_nr_sssinr_thresholds_int_array"
    const val PARAMETERS_USE_FOR_NR_SIGNAL_BAR_INT =
        "parameters_use_for_5g_nr_signal_bar_int"
    const val SIGNAL_STRENGTH_NR_NSA_USE_LTE_AS_PRIMARY_BOOL =
        "signal_strength_nr_nsa_use_lte_as_primary_bool"
    const val LTE_RSRP_THRESHOLDS_INT_ARRAY = "lte_rsrp_thresholds_int_array"

    /** 这些键只能经对应专用管理器写入，避免专家编辑器绕开 baseline 与回读契约。 */
    val specializedManagerKeys = setOf(
        FIVE_G_ICON_CONFIGURATION_STRING,
        NR_ADVANCED_THRESHOLD_BANDWIDTH_KHZ,
        INCLUDE_LTE_FOR_NR_ADVANCED_THRESHOLD,
        // 信号强度现仅由 SystemDisplayOverrideManager 写 SSRSRP；其余旧信号键仍禁专家直写。
        INFLATE_SIGNAL_STRENGTH_BOOL,
        NR_SSRSRP_THRESHOLDS_INT_ARRAY,
        NR_SSRSRQ_THRESHOLDS_INT_ARRAY,
        NR_SSSINR_THRESHOLDS_INT_ARRAY,
        PARAMETERS_USE_FOR_NR_SIGNAL_BAR_INT,
        SIGNAL_STRENGTH_NR_NSA_USE_LTE_AS_PRIMARY_BOOL,
        LTE_RSRP_THRESHOLDS_INT_ARRAY,
        WFC_SPN_FORMAT_IDX,
        WFC_DATA_SPN_FORMAT_IDX,
        WFC_FLIGHT_MODE_SPN_FORMAT_IDX,
        WFC_SPN_USE_ROOT_LOCALE,
        CARRIER_NAME_OVERRIDE,
        CARRIER_NAME_STRING,
    )

    // 国家码覆盖（TikTok 大陆 SIM 修复：写异常 ISO 绕过其归属地判断分支）
    const val SIM_COUNTRY_ISO_OVERRIDE = "sim_country_iso_override_string"

    const val SHOW_IMS_REGISTRATION_STATUS = "show_ims_registration_status_bool"
    const val ALLOW_ADDING_APNS = "allow_adding_apns_bool"
    const val SHOW_WIFI_CALLING_ICON_IN_STATUS_BAR =
        "show_wifi_calling_icon_in_status_bar_bool"
    const val ALWAYS_SHOW_DATA_RAT_ICON = "always_show_data_rat_icon_bool"
    const val SHOW_4G_FOR_LTE_DATA_ICON = "show_4g_for_lte_data_icon_bool"
    const val HIDE_LTE_PLUS_DATA_ICON = "hide_lte_plus_data_icon_bool"

    // 身份显示覆盖（对齐 carrier-ims/TurboIMS）：仅改「显示层」，不碰基带真实归属，不影响保命通信。
    // carrier_name_override_bool=true 时才启用 carrier_name_string 作为展示运营商名。
    const val CARRIER_NAME_OVERRIDE = "carrier_name_override_bool"
    const val CARRIER_NAME_STRING = "carrier_name_string"
    // IMS SIP User Agent 模板字符串（AOSP KEY_IMS_USER_AGENT_STRING，API 33+；低版本系统忽略此键无副作用）。
    const val IMS_USER_AGENT_STRING = "ims.ims_user_agent_string"

    val volteBooleanTrueKeys = listOf(
        VOLTE_AVAILABLE, VT_AVAILABLE, ENHANCED_4G_LTE_ON_BY_DEFAULT,
        EDITABLE_ENHANCED_4G_LTE, SHOW_IMS_REGISTRATION_STATUS,
    )

    val vowifiBooleanTrueKeys = listOf(
        WFC_IMS_AVAILABLE, WFC_SUPPORTS_WIFI_ONLY, WFC_DEFAULT_ROAMING_ENABLED,
        EDITABLE_WFC_MODE, EDITABLE_WFC_ROAMING_MODE,
    )

    val vonrBooleanTrueKeys = listOf(
        VONR_ENABLED,
        VONR_SETTING_VISIBILITY,
        VONR_ON_BY_DEFAULT,
    )
}

/**
 * ProvisioningManager 的 provisioning key（@SystemApi hidden，直接用其整型字面值）。
 * 这条通道底层走 `setImsProvisioningInt`，是目前唯一能让 VoLTE/VoWiFi 跨重启持久的路径。
 */
object ProvisioningKeys {
    const val KEY_VOLTE_PROVISIONING_STATUS = 10
    const val KEY_VT_PROVISIONING_STATUS = 11
    const val KEY_VOICE_OVER_WIFI_ROAMING = 26
    const val KEY_VOICE_OVER_WIFI_MODE = 27
    const val KEY_VOICE_OVER_WIFI_ENABLED = 28
    /**
     * AOSP `ProvisioningManager.KEY_VOIMS_OPT_IN_STATUS`。
     * 对齐 vvb2060/Ims 3.1：强制露出 VoLTE 选项，即使运营商配置试图隐藏。
     */
    const val KEY_VOIMS_OPT_IN_STATUS = 68

    const val PROVISIONING_VALUE_ENABLED = 1
    const val PROVISIONING_VALUE_DISABLED = 0
}
