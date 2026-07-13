package com.oneims.app.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemDisplayOverridePolicyTest {

    private val labels = SimpleFiveGDisplayLabels(
        unknownOperator = "Unknown carrier",
        uplinkEnhanced = "Uplink boost",
        superUplink = "Super uplink",
        coolFiveGPlus = "5G+ Turbo",
        coolFiveGa = "5G-A Full",
    )

    @Test
    fun fiveGIconPolicy_preservesTheFourExistingModeMappings() {
        assertEquals(
            "connected_mmwave:5G_PLUS,connected:5G,not_restricted_rrc_idle:5G",
            FiveGIconConfigurationPolicy.forConfig(
                SimpleFiveGDisplayConfig(mode = SimpleFiveGDisplayConfig.Mode.CONSERVATIVE),
            ),
        )
        assertEquals(
            SimpleFiveGDisplayConfig.DEFAULT_SYSTEM_ICON_CONFIG,
            FiveGIconConfigurationPolicy.forConfig(
                SimpleFiveGDisplayConfig(mode = SimpleFiveGDisplayConfig.Mode.CN_SPEED),
            ),
        )
        assertEquals(
            "connected_mmwave:5G_PLUS,connected:5G_PLUS,not_restricted_rrc_idle:5G_PLUS",
            FiveGIconConfigurationPolicy.forConfig(
                SimpleFiveGDisplayConfig(mode = SimpleFiveGDisplayConfig.Mode.COOL),
            ),
        )
        assertEquals(
            "connected:5G_UWB",
            FiveGIconConfigurationPolicy.forConfig(
                SimpleFiveGDisplayConfig(
                    mode = SimpleFiveGDisplayConfig.Mode.CUSTOM,
                    systemIconConfigString = " connected:5G_UWB ",
                ),
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun fiveGIconPolicy_rejectsMalformedCustomMapping() {
        FiveGIconConfigurationPolicy.validate("connected")
    }

    @Test
    fun fourAndFiveBarPresets_differOnInflateAndThresholds() {
        val four = fourBarSignalPreset()
        val five = fiveBarSignalPreset()
        assertFalse(four.inflateSignalStrength)
        assertTrue(five.inflateSignalStrength)
        assertFalse(four.nrSsrsrpThresholds.contentEquals(five.nrSsrsrpThresholds))
        assertFalse(four.lteRsrpThresholds.contentEquals(five.lteRsrpThresholds))
        assertEquals(1, four.parametersUseForNrSignalBar)
        assertEquals(1, five.parametersUseForNrSignalBar)
        assertArrayEquals(intArrayOf(-110, -90, -80, -65), four.nrSsrsrpThresholds)
        assertArrayEquals(intArrayOf(-115, -105, -95, -85), five.nrSsrsrpThresholds)
        // 能力页别名走 5 格完整预设，不再是单键 SSRSRP。
        assertTrue(
            SystemDisplayOwnershipPolicy.signalPresetsEqual(
                carrierImsSignalStrengthPreset(),
                fiveBarSignalPreset(),
            ),
        )
        assertTrue(
            SystemDisplayOwnershipPolicy.signalPresetsEqual(
                chinaMainlandSignalStrengthPreset(),
                fiveBarSignalPreset(),
            ),
        )
        assertNull(signalBarSystemPreset(ConfigStore.SignalBarDisplayMode.AUTO))
        assertNotNull(signalBarSystemPreset(ConfigStore.SignalBarDisplayMode.FOUR_BARS))
        assertNotNull(signalBarSystemPreset(ConfigStore.SignalBarDisplayMode.FIVE_BARS))
        assertFalse(
            SystemDisplayOwnershipPolicy.signalPresetsEqual(
                signalBarSystemPreset(ConfigStore.SignalBarDisplayMode.FOUR_BARS),
                signalBarSystemPreset(ConfigStore.SignalBarDisplayMode.FIVE_BARS),
            ),
        )
    }

    @Test
    fun nonCustomFiveGMode_usesDefaultThresholdsAfterLeavingCustomMode() {
        val editedThresholds = SimpleFiveGDisplayConfig(
            enabled = true,
            mode = SimpleFiveGDisplayConfig.Mode.CN_SPEED,
            plusDlThresholdMbps = 10,
            fiveGaDlThresholdMbps = 20,
        )
        val cnSpeed = resolveSimpleFiveGDisplay(
            config = editedThresholds,
            operatorName = "Carrier",
            dataNetworkType = 20,
            overrideNetworkType = 0,
            serviceStateText = "NR_SA",
            downlinkMbps = 25.0,
            uplinkMbps = 0.0,
            labels = labels,
        )
        val custom = resolveSimpleFiveGDisplay(
            config = editedThresholds.copy(mode = SimpleFiveGDisplayConfig.Mode.CUSTOM),
            operatorName = "Carrier",
            dataNetworkType = 20,
            overrideNetworkType = 0,
            serviceStateText = "NR_SA",
            downlinkMbps = 25.0,
            uplinkMbps = 0.0,
            labels = labels,
        )

        assertEquals("5G SA", cnSpeed.title)
        assertEquals("5G-A", custom.title)
    }

    @Test
    fun resolver_usesCallerProvidedLocalizedLabels() {
        val result = resolveSimpleFiveGDisplay(
            config = SimpleFiveGDisplayConfig(
                enabled = true,
                mode = SimpleFiveGDisplayConfig.Mode.COOL,
            ),
            operatorName = null,
            dataNetworkType = 20,
            overrideNetworkType = 0,
            serviceStateText = "NR_SA",
            downlinkMbps = 1_100.0,
            uplinkMbps = 350.0,
            labels = labels,
        )

        assertEquals("5G-A Full", result.title)
        assertTrue(result.networkType.startsWith("Unknown carrier"))
        assertTrue(result.speedLine.contains("Super uplink"))
    }

    @Test
    fun ownershipPolicy_refusesStaleEpochAndExternallyChangedValues() {
        assertTrue(SystemDisplayOwnershipPolicy.hasCurrentEpoch(true, "boot-7", "boot-7"))
        assertFalse(SystemDisplayOwnershipPolicy.hasCurrentEpoch(true, "boot-6", "boot-7"))
        assertFalse(SystemDisplayOwnershipPolicy.hasCurrentEpoch(false, "boot-7", "boot-7"))

        assertTrue(
            SystemDisplayOwnershipPolicy.canRestoreFiveG(
                currentValue = "pending-b",
                pendingValue = "pending-b",
                confirmedValue = "confirmed-a",
            ),
        )
        assertTrue(
            SystemDisplayOwnershipPolicy.canRestoreFiveG(
                currentValue = "confirmed-a",
                pendingValue = "pending-b",
                confirmedValue = "confirmed-a",
            ),
        )
        assertFalse(
            SystemDisplayOwnershipPolicy.canRestoreFiveG(
                currentValue = "external",
                pendingValue = "pending-b",
                confirmedValue = "confirmed-a",
            ),
        )

        val ours = fiveBarSignalPreset()
        val external = fourBarSignalPreset()
        assertTrue(SystemDisplayOwnershipPolicy.signalPresetsEqual(ours, ours.copy()))
        assertFalse(SystemDisplayOwnershipPolicy.signalPresetsEqual(external, ours))
        assertTrue(SystemDisplayOwnershipPolicy.canRestoreSignal(ours, ours, null))
        assertTrue(SystemDisplayOwnershipPolicy.canRestoreSignal(ours, external, ours.copy()))
        assertFalse(SystemDisplayOwnershipPolicy.canRestoreSignal(external, ours, ours.copy()))
    }

    @Test
    fun multiKeyOwnership_rejectsExternalInflateOrThresholdChanges() {
        val confirmed = fiveBarSignalPreset()
        assertTrue(
            SystemDisplayOwnershipPolicy.signalOwnedFieldsEqual(
                confirmed,
                confirmed.copy(),
            ),
        )
        assertTrue(
            SystemDisplayOwnershipPolicy.canRestoreSignal(
                current = confirmed,
                pending = null,
                confirmed = confirmed,
            ),
        )
        assertTrue(
            SystemDisplayOwnershipPolicy.canReapplySignal(
                current = confirmed,
                baseline = null,
                pending = null,
                confirmed = confirmed,
            ),
        )
        assertFalse(
            SystemDisplayOwnershipPolicy.canReapplySignal(
                current = fourBarSignalPreset(),
                baseline = null,
                pending = null,
                confirmed = confirmed,
            ),
        )
        assertFalse(
            SystemDisplayOwnershipPolicy.canReapplySignal(
                current = SignalBarSystemPreset(
                    inflateSignalStrength = confirmed.inflateSignalStrength,
                    nrSsrsrpThresholds = intArrayOf(-120, -110, -100, -90),
                    lteRsrpThresholds = confirmed.lteRsrpThresholds.copyOf(),
                    parametersUseForNrSignalBar = confirmed.parametersUseForNrSignalBar,
                ),
                baseline = null,
                pending = null,
                confirmed = confirmed,
            ),
        )
    }

    @Test
    fun signalBarStyleManager_mapsFourAndFivePresets() {
        assertTrue(
            SystemDisplayOwnershipPolicy.signalPresetsEqual(
                fourBarSignalPreset(),
                signalBarSystemPreset(ConfigStore.SignalBarDisplayMode.FOUR_BARS),
            ),
        )
        assertTrue(
            SystemDisplayOwnershipPolicy.signalPresetsEqual(
                fiveBarSignalPreset(),
                signalBarSystemPreset(ConfigStore.SignalBarDisplayMode.FIVE_BARS),
            ),
        )
        assertNull(signalBarSystemPreset(ConfigStore.SignalBarDisplayMode.AUTO))
    }

    @Test
    fun composeIndependent_bothOffReturnsNull() {
        assertNull(
            composeIndependentSignalPreset(
                baseline = fourBarSignalPreset(),
                adjustmentEnabled = false,
                barMode = ConfigStore.SignalBarDisplayMode.AUTO,
            ),
        )
    }

    @Test
    fun composeIndependent_adjustmentDoesNotForceFiveBars() {
        val baseline = fourBarSignalPreset()
        val composed = checkNotNull(
            composeIndependentSignalPreset(
                baseline = baseline,
                adjustmentEnabled = true,
                barMode = ConfigStore.SignalBarDisplayMode.AUTO,
            ),
        )
        assertFalse(composed.inflateSignalStrength)
        assertTrue(
            composed.nrSsrsrpThresholds.contentEquals(fiveBarSignalPreset().nrSsrsrpThresholds),
        )
    }

    @Test
    fun composeIndependent_fiveBarsKeepsBaselineThresholdsWhenAdjustmentOff() {
        val baseline = fourBarSignalPreset()
        val composed = checkNotNull(
            composeIndependentSignalPreset(
                baseline = baseline,
                adjustmentEnabled = false,
                barMode = ConfigStore.SignalBarDisplayMode.FIVE_BARS,
            ),
        )
        assertTrue(composed.inflateSignalStrength)
        assertTrue(composed.nrSsrsrpThresholds.contentEquals(baseline.nrSsrsrpThresholds))
    }

    @Test
    fun composeIndependent_bothOnMixesInflateAndSoftThresholds() {
        val baseline = fourBarSignalPreset()
        val composed = checkNotNull(
            composeIndependentSignalPreset(
                baseline = baseline,
                adjustmentEnabled = true,
                barMode = ConfigStore.SignalBarDisplayMode.FOUR_BARS,
            ),
        )
        assertFalse(composed.inflateSignalStrength)
        assertTrue(
            composed.nrSsrsrpThresholds.contentEquals(fiveBarSignalPreset().nrSsrsrpThresholds),
        )
    }
}
