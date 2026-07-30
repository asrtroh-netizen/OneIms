package com.oneims.caremin

/**
 * CARE_MIN 拉起脚本（与 OneBridge [bridgeBootShellCommand] 同构：setsid/nohup + pidof）。
 * 默认引擎仍走 OneBridge；仅 [ChannelEngine.CARE_MIN] 启用后由宿主调用本命令。
 */
object CareMinBootShell {
    const val SHELL_BOOT_OK: String = "__OK_BOOT_OK__"
    const val SHELL_BOOT_MISS: String = "__OK_BOOT_MISS__"

    fun command(
        packageName: String = CareMinHostConstants.HOST_APPLICATION_ID,
        forceRestart: Boolean = false,
    ): String {
        val nice = CareMinHostConstants.PROCESS_NICE_NAME
        val entry = CareMinHostConstants.SERVER_ENTRY_CLASS
        val start =
            "APK=\$(pm path $packageName 2>/dev/null | head -n 1 | cut -d: -f2 | tr -d '\\r'); " +
                "if [ -z \"\$APK\" ]; then printf '%s\\n' $SHELL_BOOT_MISS; exit 1; fi; " +
                "export CLASSPATH=\"\$APK\"; " +
                "(setsid /system/bin/app_process /system/bin --nice-name=$nice " +
                "$entry >/dev/null 2>&1 </dev/null &) || " +
                "(nohup /system/bin/app_process /system/bin --nice-name=$nice " +
                "$entry >/dev/null 2>&1 </dev/null &); " +
                "printf '%s\\n' $SHELL_BOOT_OK"
        return if (forceRestart) {
            "pkill -f $nice 2>/dev/null || true; $start"
        } else {
            "if pidof $nice >/dev/null 2>&1; then printf '%s\\n' $SHELL_BOOT_OK; " +
                "else $start; fi"
        }
    }
}
