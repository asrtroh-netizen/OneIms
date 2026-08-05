package com.oneims.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootPresenceProbeTest {
    @Test
    fun leftoverTempSuFileAlone_doesNotShowBadge() {
        // 旧逻辑：pathExists(tmp/su) 会亮徽标；现仅桥/可执行 su 才算。
        val snap = RootPresenceProbe.resolve(
            bridgeRoot = false,
            markerPermanent = false,
            permanentSu = false,
            temporarySu = false,
        )
        assertFalse(snap.showRootBadge)
        assertFalse(snap.any)
    }

    @Test
    fun temporarySu_showsTempBadge() {
        val snap = RootPresenceProbe.resolve(
            bridgeRoot = false,
            markerPermanent = false,
            permanentSu = false,
            temporarySu = true,
        )
        assertTrue(snap.showRootBadge)
        assertTrue(snap.temporary)
        assertFalse(snap.badgePermanent)
        assertTrue(snap.showCarrierXmlSwitch)
        assertFalse(snap.showRootBootStart)
    }

    @Test
    fun permanentMarker_showsPermanentBadge() {
        val snap = RootPresenceProbe.resolve(
            bridgeRoot = false,
            markerPermanent = true,
            permanentSu = false,
            temporarySu = true,
        )
        assertTrue(snap.showRootBadge)
        assertTrue(snap.badgePermanent)
        assertTrue(snap.showRootBootStart)
    }

    @Test
    fun temporaryOnly_hidesRootBootStart() {
        val snap = RootPresenceProbe.resolve(
            bridgeRoot = true,
            markerPermanent = false,
            permanentSu = false,
            temporarySu = false,
        )
        assertTrue(snap.any)
        assertFalse(snap.permanent)
        assertFalse(snap.showRootBootStart)
        assertTrue(snap.showCarrierXmlSwitch)
    }
}
