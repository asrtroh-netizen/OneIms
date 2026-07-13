package com.oneims.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellDelegateCleanupPolicyTest {

    @Test
    fun benign_whenDirectNoSuchMethod() {
        assertTrue(
            ShellDelegateCleanupPolicy.isBenignStopFailure(
                NoSuchMethodException("android.app.IActivityManager.stopDelegateShellPermissionIdentity []"),
            ),
        )
    }

    @Test
    fun benign_whenWrappedMessageMentionsStopDelegate() {
        assertTrue(
            ShellDelegateCleanupPolicy.isBenignStopFailure(
                IllegalStateException(
                    "override_config: NoSuchMethodException: " +
                        "android.app.IActivityManager.stopDelegateShellPermissionIdentity []",
                ),
            ),
        )
    }

    @Test
    fun notBenign_forUnrelatedCleanupFailure() {
        assertFalse(
            ShellDelegateCleanupPolicy.isBenignStopFailure(
                IllegalStateException("Permission denied clearing identity"),
            ),
        )
    }
}
