package com.oneims.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TempRootSoProviderTest {
    @Test
    fun allowsOfficialRawSoUrl() {
        val ok = TempRootSoProvider.isAllowedSoUrl(
            "https://raw.githubusercontent.com/asrtroh-netizen/OneSo-assets/main/so/CP2A.260605.012/preload-tokay-CP2A.260605.012.so",
        )
        assertTrue(ok)
    }

    @Test
    fun rejectsForeignHostCleartextAndTraversal() {
        assertFalse(
            TempRootSoProvider.isAllowedSoUrl(
                "https://evil.example/asrtroh-netizen/OneSo-assets/main/x.so",
            ),
        )
        assertFalse(
            TempRootSoProvider.isAllowedSoUrl(
                "http://raw.githubusercontent.com/asrtroh-netizen/OneSo-assets/main/so/x.so",
            ),
        )
        assertFalse(
            TempRootSoProvider.isAllowedSoUrl(
                "https://raw.githubusercontent.com/asrtroh-netizen/OneSo-assets/main/so/../secret.so",
            ),
        )
    }
}
