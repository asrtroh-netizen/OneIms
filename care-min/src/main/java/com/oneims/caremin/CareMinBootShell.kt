package com.oneims.caremin

/**
 * CARE_MIN 拉起脚本（与 OneBridge [bridgeBootShellCommand] 同构：setsid/nohup + pidof）。
 *
 * 必须传 `-Dshizuku.library.path`：ShizukuService 靠它 [System.load] librish.so；
 * 仅设 CLASSPATH 会 UnsatisfiedLinkError 秒退。
 *
 * Boot 标记必须与宿主 `OneKukuCoreComponent.SHELL_BOOT_*` 同串，激活器只认那一套。
 */
object CareMinBootShell {
    const val SHELL_BOOT_OK: String = "__OB_BOOT_OK__"
    const val SHELL_BOOT_MISS: String = "__OB_BOOT_MISS__"

    fun command(
        packageName: String = CareMinHostConstants.HOST_APPLICATION_ID,
        forceRestart: Boolean = false,
    ): String {
        val nice = CareMinHostConstants.PROCESS_NICE_NAME
        val entry = CareMinHostConstants.SERVER_ENTRY_CLASS
        // dirname(APK)/lib/$ABI 与 libshizuku starter 一致；legacy packaging 才会解压到该目录。
        val start =
            "APK=\$(pm path $packageName 2>/dev/null | head -n 1 | cut -d: -f2 | tr -d '\\r'); " +
                "if [ -z \"\$APK\" ]; then printf '%s\\n' $SHELL_BOOT_MISS; exit 1; fi; " +
                "ABI=\$(getprop ro.product.cpu.abi); " +
                "LIB=\"\$(dirname \"\$APK\")/lib/\$ABI\"; " +
                "if [ ! -f \"\$LIB/librish.so\" ]; then " +
                "for a in arm64-v8a armeabi-v7a x86_64 arm64; do " +
                "if [ -f \"\$(dirname \"\$APK\")/lib/\$a/librish.so\" ]; then LIB=\"\$(dirname \"\$APK\")/lib/\$a\"; break; fi; " +
                "done; fi; " +
                "export CLASSPATH=\"\$APK\"; " +
                "(setsid /system/bin/app_process -Dshizuku.library.path=\"\$LIB\" /system/bin --nice-name=$nice " +
                "$entry >/dev/null 2>&1 </dev/null &) || " +
                "(nohup /system/bin/app_process -Dshizuku.library.path=\"\$LIB\" /system/bin --nice-name=$nice " +
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
