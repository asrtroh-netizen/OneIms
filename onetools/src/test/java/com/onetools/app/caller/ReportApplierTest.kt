package com.onetools.app.caller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportApplierTest {
    @Test
    fun normalizePhone_stripsCountryAndNonDigits() {
        assertEquals("13800138000", ReportApplier.normalizePhoneOrNull("+86 138-0013-8000"))
        assertEquals("17000000003", ReportApplier.normalizePhoneOrNull("17000000003"))
        assertNull(ReportApplier.normalizePhoneOrNull("123"))
    }

    @Test
    fun reportTag_wireRoundTrip() {
        assertEquals(ReportTag.FRAUD, ReportTag.fromWire("fraud"))
        assertEquals(ReportTag.AGENT, ReportTag.fromWire("中介"))
        assertTrue(ReportTag.SPAM.labelZh.contains("骚扰"))
    }
}
