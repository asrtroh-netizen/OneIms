package com.oneims.app.core

/**
 * 临时 Root 实验：固定白名单命令（OneKuku 内嵌 ADB / Lite Shizuku 共用）。
 * 禁止任意用户串进入 shell 执行器。
 */
object TempRootShellCommands {
    const val REMOTE_SO: String = "/data/local/tmp/preload-comet.so"
    const val PUBLIC_SO_NAME: String = "oneims-preload-comet.so"
    const val PUBLIC_SO_SHELL: String = "/sdcard/Download/$PUBLIC_SO_NAME"
    /** 默认资产名；运行时以 [TempRootSoCatalog] 解析结果为准。 */
    const val ASSET_SO: String = "temproot/preload-comet.so"

    const val PROBE_SO: String =
        "test -f $REMOTE_SO && echo HAS_SO || echo NO_SO"

    const val VERIFY_SU_TMP: String = "/data/local/tmp/su -c /system/bin/id"
    const val VERIFY_SU_APEX: String = "/apex/com.android.virt/bin/su -c /system/bin/id"

    /**
     * 清掉卡死的 preload/`id`（多实例并发会把 pipe 配额打满 → F_SETPIPE_SZ EPERM）。
     * 通过 /proc/PID/maps 识别映射了 preload-comet.so 的 id，避免误杀无关进程。
     */
    const val KILL_STUCK_PRELOAD: String =
        "pkill -9 -f preload-comet.so 2>/dev/null; " +
            "pkill -9 -f 'LD_PRELOAD=/data/local/tmp/preload' 2>/dev/null; " +
            "for p in \$(pidof id 2>/dev/null); do " +
            "grep -q preload-comet /proc/\$p/maps 2>/dev/null && kill -9 \$p; " +
            "done; " +
            "echo KILL_OK"

    /** 绝对路径 id，避免 Drop-In 对裸 `id` 的假 Root mock。 */
    const val LD_PRELOAD: String =
        "LD_PRELOAD=$REMOTE_SO /system/bin/id"

    fun cpPublicSoToTmp(): String =
        "cp $PUBLIC_SO_SHELL $REMOTE_SO && chmod 644 $REMOTE_SO && echo CP_OK"

    fun isWhitelisted(command: String): Boolean {
        val c = command.trim()
        if (c.isEmpty()) return false
        if (c == PROBE_SO) return true
        if (c == KILL_STUCK_PRELOAD) return true
        if (c == VERIFY_SU_TMP || c == VERIFY_SU_APEX) return true
        if (c == LD_PRELOAD) return true
        if (c.startsWith("LD_PRELOAD=") &&
            c.contains(REMOTE_SO) &&
            c.endsWith("/system/bin/id")
        ) {
            return true
        }
        if (c.startsWith("cp ") &&
            c.contains("preload-comet.so") &&
            c.contains(REMOTE_SO)
        ) {
            return true
        }
        return false
    }

    fun looksLikeRootSuccess(output: String): Boolean {
        val t = output
        return t.contains("uid=0(root)") ||
            t.contains("root=1") ||
            (t.contains("uid=0") && t.contains("gid=0"))
    }
}
