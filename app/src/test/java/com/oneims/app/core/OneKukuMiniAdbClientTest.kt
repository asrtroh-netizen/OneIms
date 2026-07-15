package com.oneims.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OneKukuMiniAdbClientTest {

    @Test
    fun parsePairingInput_sixDigits() {
        val p = OneKukuMiniAdbClient.parsePairingInput("123456")!!
        assertEquals("123456", p.code)
        assertNull(p.pairPortOverride)
    }

    @Test
    fun parsePairingInput_portSpaceCode() {
        val p = OneKukuMiniAdbClient.parsePairingInput("37123 654321")!!
        assertEquals("654321", p.code)
        assertEquals(37123, p.pairPortOverride)
    }

    @Test
    fun parsePairingInput_portColonCode() {
        val p = OneKukuMiniAdbClient.parsePairingInput("37123:654321")!!
        assertEquals("654321", p.code)
        assertEquals(37123, p.pairPortOverride)
    }

    @Test
    fun isWhitelistedShell_allowsBridgeBoot() {
        val cmd = OneKukuCoreComponent.bridgeBootShellCommand()
        assertTrue(OneKukuMiniAdbClient.isWhitelistedShell(cmd))
        assertTrue(!OneKukuMiniAdbClient.isWhitelistedShell("rm -rf /"))
    }
}
