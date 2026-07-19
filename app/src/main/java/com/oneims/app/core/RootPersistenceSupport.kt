package com.oneims.app.core

import android.content.Context
import com.oneims.app.R

/**
 * Root 用户旁路能力：可观测「上次覆盖是否真正 persistent」+ 可选增强开关。
 *
 * 不改写 [CarrierConfigOverrideWriter] / Boot / Guard 主路径语义；
 * 仅在 Root 通道（[OneKukuManager.isRootChannel]）场景提供状态与偏好。
 */
object RootPersistenceSupport {

    data class Status(
        val rootChannel: Boolean,
        val enhanceEnabled: Boolean,
        /** null = 尚未记录过写入结果。 */
        val lastPersistent: Boolean?,
        val lastSuccess: Boolean?,
        val lastAtMillis: Long,
    )

    fun isRootChannel(): Boolean = OneKukuManager.isRootChannel()

    fun isEnhanceEnabled(context: Context): Boolean =
        ConfigStore.isRootPersistEnhance(context)

    fun setEnhanceEnabled(context: Context, enabled: Boolean) {
        ConfigStore.setRootPersistEnhance(context, enabled)
    }

    fun readStatus(context: Context): Status {
        val mode = ConfigStore.lastOverridePersistMode(context)
        return Status(
            rootChannel = isRootChannel(),
            enhanceEnabled = isEnhanceEnabled(context),
            lastPersistent = mode?.persistent,
            lastSuccess = mode?.success,
            lastAtMillis = mode?.atMillis ?: 0L,
        )
    }

    /**
     * 写入门面成功/部分成功后旁路记账；不影响写入本身。
     * 未开启增强且非 Root 时仍记录，便于诊断对照。
     */
    fun noteOverrideResult(context: Context, result: CarrierConfigOverrideWriter.Result) {
        ConfigStore.setLastOverridePersistMode(
            context = context,
            persistent = result.persistent,
            success = result.success,
        )
    }

    fun statusDetail(context: Context): String {
        val status = readStatus(context)
        val channel = if (status.rootChannel) {
            context.getString(R.string.root_persist_channel_yes)
        } else {
            context.getString(R.string.root_persist_channel_no)
        }
        val mode = when {
            status.lastPersistent == null ->
                context.getString(R.string.root_persist_last_unknown)
            status.lastPersistent ->
                context.getString(R.string.root_persist_last_persistent)
            else ->
                context.getString(R.string.root_persist_last_temporary)
        }
        return context.getString(R.string.root_persist_status_detail, channel, mode)
    }

    /**
     * 增强开启且当前为 Root 时，在结果文案上追加可读后缀（不改变 success/persistent）。
     */
    fun decorateResultMessage(
        context: Context,
        result: CarrierConfigOverrideWriter.Result,
    ): CarrierConfigOverrideWriter.Result {
        noteOverrideResult(context, result)
        if (!isEnhanceEnabled(context) || !isRootChannel()) {
            return result
        }
        val suffix = if (result.persistent) {
            context.getString(R.string.root_persist_msg_suffix_yes)
        } else {
            context.getString(R.string.root_persist_msg_suffix_no)
        }
        return result.copy(message = result.message + suffix)
    }
}
