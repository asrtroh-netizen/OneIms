package com.onetools.app.live.capsule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OneCapsuleStoreTest {
    @Before
    fun reset() {
        OneCapsuleStore.clear()
    }

    @Test
    fun upsertAndSwipeCyclesSessions() {
        OneCapsuleStore.upsert(OneCapsuleTemplates.meituanDelivering())
        OneCapsuleStore.upsert(OneCapsuleTemplates.didiOnTrip())
        assertEquals(2, OneCapsuleStore.snapshot().sessions.size)
        val first = OneCapsuleStore.snapshot().active?.id
        OneCapsuleStore.next()
        val second = OneCapsuleStore.snapshot().active?.id
        assertTrue(first != second)
        OneCapsuleStore.next()
        assertEquals(first, OneCapsuleStore.snapshot().active?.id)
    }

    @Test
    fun expandCollapseModes() {
        OneCapsuleStore.upsert(OneCapsuleTemplates.cainiaoParcel())
        OneCapsuleStore.expand()
        assertEquals(CapsuleDisplayMode.EXPANDED, OneCapsuleStore.snapshot().mode)
        OneCapsuleStore.collapse()
        assertEquals(CapsuleDisplayMode.PILL, OneCapsuleStore.snapshot().mode)
    }
}
