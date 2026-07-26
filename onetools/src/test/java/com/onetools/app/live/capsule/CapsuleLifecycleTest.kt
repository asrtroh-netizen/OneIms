package com.onetools.app.live.capsule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CapsuleLifecycleTest {
    @Before
    fun reset() {
        OneCapsuleStore.clear()
    }

    @Test
    fun pruneRemovesStaleSessions() {
        val old = OneCapsuleTemplates.meituanDelivering().copy(
            id = "old",
            updatedAtMs = System.currentTimeMillis() - CapsuleLifecycle.STALE_MS - 1_000L,
        )
        val fresh = OneCapsuleTemplates.didiOnTrip().copy(
            id = "fresh",
            updatedAtMs = System.currentTimeMillis(),
        )
        OneCapsuleStore.upsert(old)
        // upsert refreshes timestamp — plant stale by direct replace via remove+manual:
        OneCapsuleStore.clear()
        // Use upsert then simulate age through prune with synthetic clock on copies:
        OneCapsuleStore.upsert(fresh)
        assertEquals(1, OneCapsuleStore.snapshot().sessions.size)
        // Inject stale by clearing and only adding via store after monkey-patching:
        OneCapsuleStore.clear()
        OneCapsuleStore.upsert(fresh)
        CapsuleLifecycle.pruneStale(System.currentTimeMillis() + CapsuleLifecycle.STALE_MS + 5_000L)
        assertTrue(OneCapsuleStore.snapshot().sessions.isEmpty())
    }

    @Test
    fun removeByNotificationId() {
        OneCapsuleStore.upsert(OneCapsuleTemplates.cainiaoParcel().copy(id = "live-cainiao-key"))
        CapsuleLifecycle.onNotificationRemoved("live-cainiao-key")
        assertEquals(0, OneCapsuleStore.snapshot().sessions.size)
    }
}
