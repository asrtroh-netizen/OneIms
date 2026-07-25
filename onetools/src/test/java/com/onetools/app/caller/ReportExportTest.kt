package com.onetools.app.caller

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportExportTest {
    @Test
    fun buildJson_usesSchemaAndOmitsRawNote() {
        val reports = listOf(
            LocalReportEntity(
                id = "r1",
                phone = "17009990001",
                tag = "spam",
                note = "secret note must not leak",
                source = "manual",
                createdAt = 1720000001000L,
                applyLocal = true,
            ),
        )
        val json = ReportExport.buildJson(
            reports = reports,
            clientId = "client-test",
            appVersion = "0.1.0-lite",
        )
        val root = JSONObject(json)
        assertEquals(ReportExport.SCHEMA, root.getString("schema"))
        assertEquals("0.1.0-lite", root.getString("appVersion"))
        val item = root.getJSONArray("reports").getJSONObject(0)
        assertEquals("17009990001", item.getString("phone"))
        assertEquals("spam", item.getString("tag"))
        assertEquals("client-test", item.getString("clientId"))
        assertFalse(json.contains("secret note"))
        assertTrue(item.isNull("noteHash"))
    }

    @Test
    fun wrongTag_doesNotApplyLocalBlock() {
        assertFalse(ReportTag.WRONG_TAG.appliesLocalBlock)
        assertTrue(ReportTag.SPAM.appliesLocalBlock)
    }
}
