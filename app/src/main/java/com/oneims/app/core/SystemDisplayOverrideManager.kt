package com.oneims.app.core

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.PersistableBundle
import android.os.SystemClock
import android.provider.Settings
import com.oneims.app.R

/**
 * 信号格系统预设。总柱数由 [inflateSignalStrength] 控制（AOSP SystemUI：
 * false=默认格数，true=默认格数+1）；阈值负责 dBm→等级映射。
 */
internal data class SignalBarSystemPreset(
    val inflateSignalStrength: Boolean,
    val nrSsrsrpThresholds: IntArray,
    val lteRsrpThresholds: IntArray,
    val parametersUseForNrSignalBar: Int,
) {
    fun copy(): SignalBarSystemPreset = SignalBarSystemPreset(
        inflateSignalStrength = inflateSignalStrength,
        nrSsrsrpThresholds = nrSsrsrpThresholds.copyOf(),
        lteRsrpThresholds = lteRsrpThresholds.copyOf(),
        parametersUseForNrSignalBar = parametersUseForNrSignalBar,
    )
}

/** 固定 4 格：不 inflate，配合偏「硬」的阈值。 */
internal fun fourBarSignalPreset() = SignalBarSystemPreset(
    inflateSignalStrength = false,
    nrSsrsrpThresholds = intArrayOf(-110, -90, -80, -65),
    lteRsrpThresholds = intArrayOf(-128, -118, -108, -98),
    parametersUseForNrSignalBar = 1,
)

/** 固定 5 格：inflate=true，SystemUI 总 level +1。 */
internal fun fiveBarSignalPreset() = SignalBarSystemPreset(
    inflateSignalStrength = true,
    nrSsrsrpThresholds = intArrayOf(-115, -105, -95, -85),
    lteRsrpThresholds = intArrayOf(-125, -115, -105, -95),
    parametersUseForNrSignalBar = 1,
)

/**
 * 按独家页模式解析系统预设；AUTO 返回 null（恢复基线）。
 */
internal fun signalBarSystemPreset(
    mode: ConfigStore.SignalBarDisplayMode,
): SignalBarSystemPreset? = when (mode) {
    ConfigStore.SignalBarDisplayMode.AUTO -> null
    ConfigStore.SignalBarDisplayMode.FOUR_BARS -> fourBarSignalPreset()
    ConfigStore.SignalBarDisplayMode.FIVE_BARS -> fiveBarSignalPreset()
}

/** 能力页「信号强度调整」开启时走 5 格完整预设（含 inflate）。 */
internal fun carrierImsSignalStrengthPreset() = fiveBarSignalPreset()

/** @deprecated 旧名保留；现与 [fiveBarSignalPreset] 一致。 */
internal fun chinaMainlandSignalStrengthPreset() = fiveBarSignalPreset()

internal object FiveGIconConfigurationPolicy {
    private const val CONSERVATIVE =
        "connected_mmwave:5G_PLUS,connected:5G,not_restricted_rrc_idle:5G"
    private const val COOL =
        "connected_mmwave:5G_PLUS,connected:5G_PLUS,not_restricted_rrc_idle:5G_PLUS"
    private val tokenPattern = Regex("[a-zA-Z0-9_]+")

    fun forConfig(config: SimpleFiveGDisplayConfig): String = when (config.mode) {
        SimpleFiveGDisplayConfig.Mode.CONSERVATIVE -> CONSERVATIVE
        SimpleFiveGDisplayConfig.Mode.CN_SPEED -> SimpleFiveGDisplayConfig.DEFAULT_SYSTEM_ICON_CONFIG
        SimpleFiveGDisplayConfig.Mode.COOL -> COOL
        SimpleFiveGDisplayConfig.Mode.CUSTOM -> validate(config.systemIconConfigString)
        else -> error("Unsupported 5G display mode: ${config.mode}")
    }

    fun validate(raw: String): String {
        val normalized = raw.trim()
        require(normalized.isNotEmpty()) { "System 5G icon configuration is empty" }
        require(normalized.length <= 1024) { "System 5G icon configuration is too long" }
        require(normalized.none(Char::isISOControl)) {
            "System 5G icon configuration contains control characters"
        }
        normalized.split(',').forEach { entry ->
            val pair = entry.trim().split(':')
            require(pair.size == 2 && pair.all { it.isNotBlank() }) {
                "Invalid 5G icon mapping: $entry"
            }
            require(tokenPattern.matches(pair[0]) && tokenPattern.matches(pair[1])) {
                "Invalid 5G icon mapping token: $entry"
            }
        }
        return normalized
    }
}

internal object SystemDisplayOwnershipPolicy {
    fun hasCurrentEpoch(hasBaseline: Boolean, storedEpoch: String?, currentEpoch: String): Boolean =
        hasBaseline && storedEpoch == currentEpoch

    fun canRestoreFiveG(
        currentValue: String?,
        pendingValue: String?,
        confirmedValue: String?,
    ): Boolean = currentValue != null &&
        (currentValue == pendingValue || currentValue == confirmedValue)

    fun signalPresetsEqual(
        first: SignalBarSystemPreset?,
        second: SignalBarSystemPreset?,
    ): Boolean = signalOwnedFieldsEqual(first, second)

    fun signalOwnedFieldsEqual(
        first: SignalBarSystemPreset?,
        second: SignalBarSystemPreset?,
    ): Boolean = first != null &&
        second != null &&
        first.inflateSignalStrength == second.inflateSignalStrength &&
        first.parametersUseForNrSignalBar == second.parametersUseForNrSignalBar &&
        first.nrSsrsrpThresholds.contentEquals(second.nrSsrsrpThresholds) &&
        first.lteRsrpThresholds.contentEquals(second.lteRsrpThresholds)

    fun canRestoreSignal(
        current: SignalBarSystemPreset?,
        pending: SignalBarSystemPreset?,
        confirmed: SignalBarSystemPreset?,
    ): Boolean =
        signalOwnedFieldsEqual(current, pending) ||
            signalOwnedFieldsEqual(current, confirmed)

    fun canReapplySignal(
        current: SignalBarSystemPreset?,
        baseline: SignalBarSystemPreset?,
        pending: SignalBarSystemPreset?,
        confirmed: SignalBarSystemPreset?,
    ): Boolean =
        signalOwnedFieldsEqual(current, baseline) ||
            signalOwnedFieldsEqual(current, pending) ||
            signalOwnedFieldsEqual(current, confirmed)
}

/**
 * 系统显示层覆盖的唯一入口。AOSP CarrierConfig override 只能合并 partial bundle 或清空
 * 整个 subId 的 override，不能安全删除单个键。因此 AUTO/关闭使用“首次写入前有效值”
 * 作为每卡基线回写，绝不调用 bundle=null 误清 IMS 等其他模块覆盖。
 */
object SystemDisplayOverrideManager {
    private const val BASELINE_PREFS = "oneims_system_display_baselines"
    private const val KEY_5G_HAS = "five_g_has"
    private const val KEY_5G_VALUE = "five_g_value"
    private const val KEY_5G_PENDING = "five_g_pending"
    private const val KEY_5G_LAST_APPLIED = "five_g_last_applied"
    private const val KEY_5G_BOOT_EPOCH = "five_g_boot_epoch"
    private const val KEY_SIGNAL_HAS = "signal_has"
    private const val KEY_SIGNAL_INFLATE = "signal_inflate"
    private const val KEY_SIGNAL_NR_RSRP = "signal_nr_rsrp"
    private const val KEY_SIGNAL_LTE_RSRP = "signal_lte_rsrp"
    private const val KEY_SIGNAL_PARAMETERS = "signal_parameters"
    private const val KEY_SIGNAL_LAST_INFLATE = "signal_last_inflate"
    private const val KEY_SIGNAL_LAST_NR_RSRP = "signal_last_nr_rsrp"
    private const val KEY_SIGNAL_LAST_LTE_RSRP = "signal_last_lte_rsrp"
    private const val KEY_SIGNAL_LAST_PARAMETERS = "signal_last_parameters"
    private const val KEY_SIGNAL_PENDING_INFLATE = "signal_pending_inflate"
    private const val KEY_SIGNAL_PENDING_NR_RSRP = "signal_pending_nr_rsrp"
    private const val KEY_SIGNAL_PENDING_LTE_RSRP = "signal_pending_lte_rsrp"
    private const val KEY_SIGNAL_PENDING_PARAMETERS = "signal_pending_parameters"
    private const val KEY_SIGNAL_BOOT_EPOCH = "signal_boot_epoch"

    fun read5GIconConfig(context: Context, subId: Int): String? {
        requireValidSubId(subId)
        return SystemApiBroker.getCarrierConfig(context, subId)
            ?.getString(CarrierConfigKeys.FIVE_G_ICON_CONFIGURATION_STRING)
    }

    @Synchronized
    fun apply5GIconConfig(context: Context, subId: Int, configString: String): String {
        requireValidSubId(subId)
        val validated = FiveGIconConfigurationPolicy.validate(configString)
        captureFiveGBaselineOnce(context, subId)
        recordFiveGIntent(context, subId, validated)
        val overrides = PersistableBundle().apply {
            putString(CarrierConfigKeys.FIVE_G_ICON_CONFIGURATION_STRING, validated)
        }
        SystemApiBroker.overrideConfig(context, subId, overrides, persistent = false)
        check(verify5GIconConfig(context, subId, validated)) {
            "5G icon CarrierConfig readback mismatch for subId=$subId"
        }
        confirmFiveGApplied(context, subId, validated)
        return validated
    }

    fun verify5GIconConfig(context: Context, subId: Int, expectedConfig: String): Boolean {
        requireValidSubId(subId)
        return read5GIconConfig(context, subId) == expectedConfig
    }

    @Synchronized
    fun clear5GIconConfig(context: Context, subId: Int): Boolean {
        requireValidSubId(subId)
        val baseline = readFiveGBaseline(context, subId) ?: return false
        val pending = readFiveGPending(context, subId)
        val confirmed = readFiveGConfirmed(context, subId)
        val current = read5GIconConfig(context, subId)
        if (!SystemDisplayOwnershipPolicy.canRestoreFiveG(current, pending, confirmed)) {
            // 当前值已经不再由 OneIms 拥有，不能用旧 baseline 覆盖其他模块/运营商的新决定。
            clearFiveGOwnership(context, subId)
            return false
        }
        val overrides = PersistableBundle().apply {
            putString(CarrierConfigKeys.FIVE_G_ICON_CONFIGURATION_STRING, baseline)
        }
        SystemApiBroker.overrideConfig(context, subId, overrides, persistent = false)
        check(verify5GIconConfig(context, subId, baseline)) {
            "5G icon baseline readback mismatch for subId=$subId"
        }
        clearFiveGOwnership(context, subId)
        return true
    }

    /**
     * 按用户要求先保存原有本地策略，再尝试系统写入。系统失败时本地配置仍保留，
     * 异常会明确带出失败阶段，调用方不得把它包装成成功。
     */
    fun applyFiveGDisplay(
        context: Context,
        subId: Int,
        config: SimpleFiveGDisplayConfig,
    ): String {
        requireValidSubId(subId)
        val systemConfig = if (config.enabled) {
            FiveGIconConfigurationPolicy.forConfig(config)
        } else {
            null
        }
        val committedConfig = if (
            config.mode == SimpleFiveGDisplayConfig.Mode.CUSTOM &&
            systemConfig != null
        ) {
            config.copy(systemIconConfigString = systemConfig)
        } else {
            config
        }
        ConfigStore.setFiveGDisplayConfig(context, committedConfig)
        return runCatching {
            if (!committedConfig.enabled) {
                val restored = clear5GIconConfig(context, subId)
                if (restored) {
                    context.getString(R.string.five_g_system_restored)
                } else {
                    context.getString(R.string.five_g_local_saved_no_system_override)
                }
            } else {
                apply5GIconConfig(
                    context = context,
                    subId = subId,
                    configString = checkNotNull(systemConfig),
                )
                context.getString(R.string.five_g_system_applied)
            }
        }.getOrElse { error ->
            throw IllegalStateException(
                context.getString(
                    R.string.five_g_system_apply_failed,
                    OperationErrors.describe(error),
                ),
                error,
            )
        }
    }

    @Synchronized
    fun applySignalStrengthConfig(
        context: Context,
        subId: Int,
        enabled: Boolean,
    ): Boolean {
        requireValidSubId(subId)
        if (!enabled) {
            return restoreSignalBaseline(context, subId)
        }
        return applySignalStrengthPreset(context, subId, fiveBarSignalPreset())
    }

    @Synchronized
    internal fun applySignalStrengthPreset(
        context: Context,
        subId: Int,
        preset: SignalBarSystemPreset,
    ): Boolean {
        requireValidSubId(subId)
        val current = checkNotNull(readCurrentSignalPreset(context, subId)) {
            "Signal CarrierConfig is unavailable for subId=$subId"
        }
        val baseline = readSignalBaseline(context, subId)
        if (baseline == null) {
            captureSignalBaseline(context, subId, current)
        } else {
            val pending = readSignalPending(context, subId)
            val confirmed = readSignalConfirmed(context, subId)
            if (!SystemDisplayOwnershipPolicy.canReapplySignal(
                    current = current,
                    baseline = baseline,
                    pending = pending,
                    confirmed = confirmed,
                )
            ) {
                clearSignalOwnership(context, subId)
                error("Signal CarrierConfig changed externally for subId=$subId")
            }
        }
        recordSignalIntent(context, subId, preset)
        SystemApiBroker.overrideConfig(
            context,
            subId,
            preset.toPersistableBundle(),
            persistent = false,
        )
        check(verifySignalBarConfig(context, subId, preset)) {
            "Signal threshold CarrierConfig readback mismatch for subId=$subId"
        }
        confirmSignalApplied(context, subId, preset)
        return true
    }

    internal fun verifySignalBarConfig(
        context: Context,
        subId: Int,
        expected: SignalBarSystemPreset,
    ): Boolean {
        requireValidSubId(subId)
        return SystemDisplayOwnershipPolicy.signalPresetsEqual(
            readCurrentSignalPreset(context, subId),
            expected,
        )
    }

    internal fun verifySignalOwnedConfig(
        context: Context,
        subId: Int,
        expected: SignalBarSystemPreset,
    ): Boolean {
        requireValidSubId(subId)
        return SystemDisplayOwnershipPolicy.signalOwnedFieldsEqual(
            readCurrentSignalPreset(context, subId),
            expected,
        )
    }

    /**
     * @param enabled 是否把信号阈值写入系统（与 5G NR 耦合时由调用方决定）
     * @param preferenceEnabled 能力页开关偏好；可与 [enabled] 解耦，避免 NR 关闭时误清用户勾选
     * @param preferenceMode 独家页精确模式；开启写入时按该模式选四/五格预设，避免 FOUR 被冲成 FIVE
     */
    fun applySignalStrengthAdjustment(
        context: Context,
        subId: Int,
        enabled: Boolean,
        preferenceEnabled: Boolean = enabled,
        preferenceMode: ConfigStore.SignalBarDisplayMode? = null,
    ): String {
        requireValidSubId(subId)
        return runCatching {
            val modeToPersist = when {
                !preferenceEnabled -> ConfigStore.SignalBarDisplayMode.AUTO
                preferenceMode != null && preferenceMode.adjustmentEnabled -> preferenceMode
                else -> {
                    val current = ConfigStore.signalBarDisplayMode(context, subId)
                    if (current == ConfigStore.SignalBarDisplayMode.FOUR_BARS) {
                        ConfigStore.SignalBarDisplayMode.FOUR_BARS
                    } else {
                        ConfigStore.SignalBarDisplayMode.FIVE_BARS
                    }
                }
            }
            val systemChanged = if (!enabled) {
                applySignalStrengthConfig(context, subId, enabled = false)
            } else {
                val preset = checkNotNull(signalBarSystemPreset(modeToPersist)) {
                    "Signal bar preset missing for mode=$modeToPersist"
                }
                applySignalStrengthPreset(context, subId, preset)
            }
            ConfigStore.setSignalBarDisplayMode(context, subId, modeToPersist)
            when {
                !enabled && systemChanged ->
                    context.getString(R.string.signal_bar_system_restored)
                !enabled ->
                    context.getString(R.string.signal_bar_local_saved_no_system_override)
                else -> context.getString(R.string.signal_bar_system_applied)
            }
        }.getOrElse { error ->
            throw IllegalStateException(
                context.getString(
                    R.string.signal_bar_system_apply_failed,
                    OperationErrors.describe(error),
                ),
                error,
            )
        }
    }

    /**
     * 独家页「信号格显示样式」入口已迁至 [SignalBarSystemStyleManager]；
     * 保留本方法以免旧调用点断裂。
     */
    fun applySignalBarDisplay(
        context: Context,
        subId: Int,
        mode: ConfigStore.SignalBarDisplayMode,
    ): String = SignalBarSystemStyleManager.apply(context, subId, mode)

    /** 供样式 Manager 回读当前卡 CarrierConfig 信号预设。 */
    internal fun peekSignalPreset(context: Context, subId: Int): SignalBarSystemPreset? =
        readCurrentSignalPreset(context, subId)

    /** 供样式 Manager 清除本模块信号 override（回写基线）。 */
    internal fun restoreSignalBarOverride(context: Context, subId: Int): Boolean =
        restoreSignalBaseline(context, subId)

    @SuppressLint("ApplySharedPref")
    private fun captureFiveGBaselineOnce(context: Context, subId: Int) {
        val prefs = baselinePrefs(context)
        val epoch = currentBootEpoch(context)
        if (SystemDisplayOwnershipPolicy.hasCurrentEpoch(
                hasBaseline = prefs.getBoolean(subKey(KEY_5G_HAS, subId), false),
                storedEpoch = prefs.getString(subKey(KEY_5G_BOOT_EPOCH, subId), null),
                currentEpoch = epoch,
            )
        ) {
            return
        }
        clearFiveGOwnership(context, subId)
        val current = checkNotNull(read5GIconConfig(context, subId)) {
            "System has no readable 5G icon configuration for subId=$subId"
        }
        prefs.edit()
            .putString(subKey(KEY_5G_VALUE, subId), current)
            .putString(subKey(KEY_5G_BOOT_EPOCH, subId), epoch)
            .putBoolean(subKey(KEY_5G_HAS, subId), true)
            // 系统写入前必须确认基线已落盘；异步 apply 可能在进程退出时丢失回滚依据。
            .commit()
            .also { saved -> check(saved) { "Failed to persist 5G display baseline" } }
    }

    private fun readFiveGBaseline(context: Context, subId: Int): String? {
        val prefs = baselinePrefs(context)
        if (!prefs.getBoolean(subKey(KEY_5G_HAS, subId), false)) return null
        if (
            prefs.getString(subKey(KEY_5G_BOOT_EPOCH, subId), null) !=
            currentBootEpoch(context)
        ) {
            clearFiveGOwnership(context, subId)
            return null
        }
        return prefs.getString(subKey(KEY_5G_VALUE, subId), null)
    }

    @SuppressLint("ApplySharedPref")
    private fun recordFiveGIntent(context: Context, subId: Int, value: String) {
        val saved = baselinePrefs(context).edit()
            .putString(subKey(KEY_5G_PENDING, subId), value)
            .commit()
        check(saved) { "Failed to persist pending 5G display ownership" }
    }

    @SuppressLint("ApplySharedPref")
    private fun confirmFiveGApplied(context: Context, subId: Int, value: String) {
        val saved = baselinePrefs(context).edit()
            .putString(subKey(KEY_5G_LAST_APPLIED, subId), value)
            .remove(subKey(KEY_5G_PENDING, subId))
            .commit()
        check(saved) { "Failed to confirm 5G display ownership" }
    }

    private fun readFiveGPending(context: Context, subId: Int): String? =
        baselinePrefs(context).getString(subKey(KEY_5G_PENDING, subId), null)

    private fun readFiveGConfirmed(context: Context, subId: Int): String? =
        baselinePrefs(context).getString(subKey(KEY_5G_LAST_APPLIED, subId), null)

    @SuppressLint("ApplySharedPref")
    private fun captureSignalBaseline(
        context: Context,
        subId: Int,
        current: SignalBarSystemPreset,
    ) {
        val prefs = baselinePrefs(context)
        val epoch = currentBootEpoch(context)
        if (SystemDisplayOwnershipPolicy.hasCurrentEpoch(
                hasBaseline = prefs.getBoolean(subKey(KEY_SIGNAL_HAS, subId), false),
                storedEpoch = prefs.getString(subKey(KEY_SIGNAL_BOOT_EPOCH, subId), null),
                currentEpoch = epoch,
            )
        ) {
            return
        }
        clearSignalOwnership(context, subId)
        prefs.edit()
            .putBoolean(subKey(KEY_SIGNAL_INFLATE, subId), current.inflateSignalStrength)
            .putString(
                subKey(KEY_SIGNAL_NR_RSRP, subId),
                current.nrSsrsrpThresholds.joinToString(","),
            )
            .putString(
                subKey(KEY_SIGNAL_LTE_RSRP, subId),
                current.lteRsrpThresholds.joinToString(","),
            )
            .putInt(subKey(KEY_SIGNAL_PARAMETERS, subId), current.parametersUseForNrSignalBar)
            .putString(subKey(KEY_SIGNAL_BOOT_EPOCH, subId), epoch)
            .putBoolean(subKey(KEY_SIGNAL_HAS, subId), true)
            .commit()
            .also { saved -> check(saved) { "Failed to persist signal display baseline" } }
    }

    private fun restoreSignalBaseline(context: Context, subId: Int): Boolean {
        val baseline = readSignalBaseline(context, subId) ?: return false
        val current = readCurrentSignalPreset(context, subId)
        val pending = readSignalPending(context, subId)
        val confirmed = readSignalConfirmed(context, subId)
        if (!SystemDisplayOwnershipPolicy.canRestoreSignal(current, pending, confirmed)) {
            clearSignalOwnership(context, subId)
            return false
        }
        SystemApiBroker.overrideConfig(
            context,
            subId,
            baseline.toPersistableBundle(),
            persistent = false,
        )
        check(verifySignalOwnedConfig(context, subId, baseline)) {
            "Signal baseline readback mismatch for subId=$subId"
        }
        clearSignalOwnership(context, subId)
        return true
    }

    private fun readSignalBaseline(context: Context, subId: Int): SignalBarSystemPreset? {
        val prefs = baselinePrefs(context)
        if (!prefs.getBoolean(subKey(KEY_SIGNAL_HAS, subId), false)) return null
        if (
            prefs.getString(subKey(KEY_SIGNAL_BOOT_EPOCH, subId), null) !=
            currentBootEpoch(context)
        ) {
            clearSignalOwnership(context, subId)
            return null
        }
        return readStoredSignalPreset(
            prefs = prefs,
            subId = subId,
            inflateKey = KEY_SIGNAL_INFLATE,
            nrKey = KEY_SIGNAL_NR_RSRP,
            lteKey = KEY_SIGNAL_LTE_RSRP,
            parametersKey = KEY_SIGNAL_PARAMETERS,
            requireInflateKey = false,
        )
    }

    private fun readCurrentSignalPreset(
        context: Context,
        subId: Int,
    ): SignalBarSystemPreset? {
        val current = SystemApiBroker.getCarrierConfig(context, subId) ?: return null
        val nr = current.getIntArray(CarrierConfigKeys.NR_SSRSRP_THRESHOLDS_INT_ARRAY)
            ?: return null
        val inflate = if (current.containsKey(CarrierConfigKeys.INFLATE_SIGNAL_STRENGTH_BOOL)) {
            current.getBoolean(CarrierConfigKeys.INFLATE_SIGNAL_STRENGTH_BOOL)
        } else {
            false
        }
        val lte = current.getIntArray(CarrierConfigKeys.LTE_RSRP_THRESHOLDS_INT_ARRAY)
            ?: intArrayOf(-140, -128, -118, -108)
        val parameters =
            if (current.containsKey(CarrierConfigKeys.PARAMETERS_USE_FOR_NR_SIGNAL_BAR_INT)) {
                current.getInt(CarrierConfigKeys.PARAMETERS_USE_FOR_NR_SIGNAL_BAR_INT)
            } else {
                1
            }
        return SignalBarSystemPreset(
            inflateSignalStrength = inflate,
            nrSsrsrpThresholds = nr,
            lteRsrpThresholds = lte,
            parametersUseForNrSignalBar = parameters,
        )
    }

    @SuppressLint("ApplySharedPref")
    private fun recordSignalIntent(
        context: Context,
        subId: Int,
        preset: SignalBarSystemPreset,
    ) {
        val saved = baselinePrefs(context).edit()
            .putBoolean(subKey(KEY_SIGNAL_PENDING_INFLATE, subId), preset.inflateSignalStrength)
            .putString(
                subKey(KEY_SIGNAL_PENDING_NR_RSRP, subId),
                preset.nrSsrsrpThresholds.joinToString(","),
            )
            .putString(
                subKey(KEY_SIGNAL_PENDING_LTE_RSRP, subId),
                preset.lteRsrpThresholds.joinToString(","),
            )
            .putInt(
                subKey(KEY_SIGNAL_PENDING_PARAMETERS, subId),
                preset.parametersUseForNrSignalBar,
            )
            .commit()
        check(saved) { "Failed to persist pending signal display ownership" }
    }

    @SuppressLint("ApplySharedPref")
    private fun confirmSignalApplied(
        context: Context,
        subId: Int,
        preset: SignalBarSystemPreset,
    ) {
        val saved = baselinePrefs(context).edit()
            .putBoolean(subKey(KEY_SIGNAL_LAST_INFLATE, subId), preset.inflateSignalStrength)
            .putString(
                subKey(KEY_SIGNAL_LAST_NR_RSRP, subId),
                preset.nrSsrsrpThresholds.joinToString(","),
            )
            .putString(
                subKey(KEY_SIGNAL_LAST_LTE_RSRP, subId),
                preset.lteRsrpThresholds.joinToString(","),
            )
            .putInt(
                subKey(KEY_SIGNAL_LAST_PARAMETERS, subId),
                preset.parametersUseForNrSignalBar,
            )
            .remove(subKey(KEY_SIGNAL_PENDING_INFLATE, subId))
            .remove(subKey(KEY_SIGNAL_PENDING_NR_RSRP, subId))
            .remove(subKey(KEY_SIGNAL_PENDING_LTE_RSRP, subId))
            .remove(subKey(KEY_SIGNAL_PENDING_PARAMETERS, subId))
            .commit()
        check(saved) { "Failed to confirm signal display ownership" }
    }

    private fun readSignalPending(context: Context, subId: Int): SignalBarSystemPreset? =
        readSignalOwnershipPreset(context, subId, pending = true)

    private fun readSignalConfirmed(context: Context, subId: Int): SignalBarSystemPreset? =
        readSignalOwnershipPreset(context, subId, pending = false)

    private fun readSignalOwnershipPreset(
        context: Context,
        subId: Int,
        pending: Boolean,
    ): SignalBarSystemPreset? {
        val prefs = baselinePrefs(context)
        return readStoredSignalPreset(
            prefs = prefs,
            subId = subId,
            inflateKey = if (pending) KEY_SIGNAL_PENDING_INFLATE else KEY_SIGNAL_LAST_INFLATE,
            nrKey = if (pending) KEY_SIGNAL_PENDING_NR_RSRP else KEY_SIGNAL_LAST_NR_RSRP,
            lteKey = if (pending) KEY_SIGNAL_PENDING_LTE_RSRP else KEY_SIGNAL_LAST_LTE_RSRP,
            parametersKey = if (pending) {
                KEY_SIGNAL_PENDING_PARAMETERS
            } else {
                KEY_SIGNAL_LAST_PARAMETERS
            },
            requireInflateKey = true,
        )
    }

    private fun readStoredSignalPreset(
        prefs: SharedPreferences,
        subId: Int,
        inflateKey: String,
        nrKey: String,
        lteKey: String,
        parametersKey: String,
        requireInflateKey: Boolean,
    ): SignalBarSystemPreset? {
        val nr = prefs.getString(subKey(nrKey, subId), null)?.toIntArrayOrNull() ?: return null
        if (requireInflateKey && !prefs.contains(subKey(inflateKey, subId))) return null
        val inflate = prefs.getBoolean(subKey(inflateKey, subId), false)
        val lte = prefs.getString(subKey(lteKey, subId), null)?.toIntArrayOrNull()
            ?: intArrayOf(-140, -128, -118, -108)
        val parameters = if (prefs.contains(subKey(parametersKey, subId))) {
            prefs.getInt(subKey(parametersKey, subId), 1)
        } else {
            1
        }
        return SignalBarSystemPreset(
            inflateSignalStrength = inflate,
            nrSsrsrpThresholds = nr,
            lteRsrpThresholds = lte,
            parametersUseForNrSignalBar = parameters,
        )
    }

    /**
     * 应急还原会清空该卡全部非持久 override；同步结束显示键所有权，下一次应用重新取基线。
     */
    @Synchronized
    fun onAllOverridesCleared(context: Context, subId: Int) {
        clearFiveGOwnership(context, subId)
        clearSignalOwnership(context, subId)
    }

    @SuppressLint("ApplySharedPref")
    private fun clearFiveGOwnership(context: Context, subId: Int) {
        val saved = baselinePrefs(context).edit()
            .remove(subKey(KEY_5G_HAS, subId))
            .remove(subKey(KEY_5G_VALUE, subId))
            .remove(subKey(KEY_5G_PENDING, subId))
            .remove(subKey(KEY_5G_LAST_APPLIED, subId))
            .remove(subKey(KEY_5G_BOOT_EPOCH, subId))
            .commit()
        check(saved) { "Failed to clear 5G display ownership" }
    }

    @SuppressLint("ApplySharedPref")
    private fun clearSignalOwnership(context: Context, subId: Int) {
        val saved = baselinePrefs(context).edit()
            .remove(subKey(KEY_SIGNAL_HAS, subId))
            .remove(subKey(KEY_SIGNAL_INFLATE, subId))
            .remove(subKey(KEY_SIGNAL_NR_RSRP, subId))
            .remove(subKey(KEY_SIGNAL_LTE_RSRP, subId))
            .remove(subKey(KEY_SIGNAL_PARAMETERS, subId))
            .remove(subKey(KEY_SIGNAL_LAST_INFLATE, subId))
            .remove(subKey(KEY_SIGNAL_LAST_NR_RSRP, subId))
            .remove(subKey(KEY_SIGNAL_LAST_LTE_RSRP, subId))
            .remove(subKey(KEY_SIGNAL_LAST_PARAMETERS, subId))
            .remove(subKey(KEY_SIGNAL_PENDING_INFLATE, subId))
            .remove(subKey(KEY_SIGNAL_PENDING_NR_RSRP, subId))
            .remove(subKey(KEY_SIGNAL_PENDING_LTE_RSRP, subId))
            .remove(subKey(KEY_SIGNAL_PENDING_PARAMETERS, subId))
            .remove(subKey(KEY_SIGNAL_BOOT_EPOCH, subId))
            // 历史键（旧版曾写过）：一并清掉，避免脏 ownership 干扰。
            .remove(subKey("signal_nr_rsrq", subId))
            .remove(subKey("signal_nr_sinr", subId))
            .remove(subKey("signal_nsa_primary", subId))
            .remove(subKey("signal_last_nr_rsrq", subId))
            .remove(subKey("signal_last_nr_sinr", subId))
            .remove(subKey("signal_last_nsa_primary", subId))
            .remove(subKey("signal_pending_nr_rsrq", subId))
            .remove(subKey("signal_pending_nr_sinr", subId))
            .remove(subKey("signal_pending_nsa_primary", subId))
            .commit()
        check(saved) { "Failed to clear signal display ownership" }
    }

    private fun currentBootEpoch(context: Context): String {
        val bootCount = runCatching {
            Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.BOOT_COUNT,
                -1,
            )
        }.getOrDefault(-1)
        if (bootCount >= 0) return "count:$bootCount"
        // 极少数 ROM 不暴露 BOOT_COUNT；分钟级启动时间原点仍能在同一开机周期保持稳定。
        val bootOriginMinutes =
            (System.currentTimeMillis() - SystemClock.elapsedRealtime()) / 60_000L
        return "origin:$bootOriginMinutes"
    }

    private fun SignalBarSystemPreset.toPersistableBundle() = PersistableBundle().apply {
        putBoolean(CarrierConfigKeys.INFLATE_SIGNAL_STRENGTH_BOOL, inflateSignalStrength)
        putIntArray(CarrierConfigKeys.NR_SSRSRP_THRESHOLDS_INT_ARRAY, nrSsrsrpThresholds)
        putIntArray(CarrierConfigKeys.LTE_RSRP_THRESHOLDS_INT_ARRAY, lteRsrpThresholds)
        putInt(
            CarrierConfigKeys.PARAMETERS_USE_FOR_NR_SIGNAL_BAR_INT,
            parametersUseForNrSignalBar,
        )
    }

    private fun String.toIntArrayOrNull(): IntArray? {
        val values = split(',').map { it.trim().toIntOrNull() ?: return null }
        return values.takeIf { it.size == 4 }?.toIntArray()
    }

    private fun baselinePrefs(context: Context) =
        context.getSharedPreferences(BASELINE_PREFS, Context.MODE_PRIVATE)

    private fun subKey(prefix: String, subId: Int) = "${prefix}_$subId"

    private fun requireValidSubId(subId: Int) {
        require(subId >= 0) { "Invalid subscription id: $subId" }
    }
}
