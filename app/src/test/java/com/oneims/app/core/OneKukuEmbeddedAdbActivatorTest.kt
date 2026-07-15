package com.oneims.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OneKukuEmbeddedAdbActivatorTest {

    @Test
    fun shellBootOutput_requiresStandaloneStartedLine() {
        assertTrue(OneKukuEmbeddedAdbActivator.isShellBootOutputOk("OneBridge_started\n"))
        assertTrue(
            OneKukuEmbeddedAdbActivator.isShellBootOutputOk(
                "${OneKukuCoreComponent.SHELL_BOOT_OK}\n",
            ),
        )
        assertFalse(OneKukuEmbeddedAdbActivator.isShellBootOutputOk(""))
        assertFalse(OneKukuEmbeddedAdbActivator.isShellBootOutputOk("OneBridge_missing"))
        // 最后一行状态为准
        assertTrue(
            OneKukuEmbeddedAdbActivator.isShellBootOutputOk("OneBridge_missing\nOneBridge_started"),
        )
        assertFalse(
            OneKukuEmbeddedAdbActivator.isShellBootOutputOk("OneBridge_started\nOneBridge_missing"),
        )
    }

    @Test
    fun shellBootStatus_ignoresCommandEchoContainingMarkerSubstrings() {
        // 真机 2.0.22：PTY 回显整段脚本，正文含 echo OneBridge_missing / started
        val echoedCmd =
            "pkill -f onebridge_server 2>/dev/null || true; " +
                "APK=\$(pm path com.oneims.app ...); " +
                "if [ -z \"\$APK\" ]; then echo OneBridge_missing; exit 1; fi; " +
                "echo OneBridge_started"
        assertEquals(
            OneKukuEmbeddedAdbActivator.ShellBootStatus.UNKNOWN,
            OneKukuEmbeddedAdbActivator.shellBootStatus(echoedCmd),
        )
        assertFalse(OneKukuEmbeddedAdbActivator.isShellBootOutputOk(echoedCmd))

        val withRealOk = echoedCmd + "\n" + OneKukuCoreComponent.SHELL_BOOT_OK + "\n"
        assertEquals(
            OneKukuEmbeddedAdbActivator.ShellBootStatus.OK,
            OneKukuEmbeddedAdbActivator.shellBootStatus(withRealOk),
        )
        assertTrue(OneKukuEmbeddedAdbActivator.isShellBootOutputOk(withRealOk))
    }

    @Test
    fun bridgeBootCommand_usesStandalonePrintfMarkers() {
        val cmd = OneKukuCoreComponent.bridgeBootShellCommand()
        assertTrue(cmd.contains(OneKukuCoreComponent.SHELL_BOOT_OK))
        assertTrue(cmd.contains(OneKukuCoreComponent.SHELL_BOOT_MISS))
        assertTrue(cmd.contains("printf"))
        assertTrue(cmd.contains("pidof onebridge_server"))
        assertFalse(cmd.contains("pkill"))
        assertFalse(cmd.contains("nohup"))
    }
}
