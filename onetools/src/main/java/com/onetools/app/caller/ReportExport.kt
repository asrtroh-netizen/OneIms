package com.onetools.app.caller

import org.json.JSONArray
import org.json.JSONObject

/**
 * Phase-2 community export — schema `onetools.report.v1`.
 * Never includes raw notes (privacy); only optional noteHash placeholder.
 */
object ReportExport {
    const val SCHEMA = "onetools.report.v1"

    fun buildJson(
        reports: List<LocalReportEntity>,
        clientId: String,
        appVersion: String,
    ): String {
        val arr = JSONArray()
        for (r in reports) {
            // wrong_tag is correction signal; still exportable for demotion.
            arr.put(
                JSONObject()
                    .put("phone", r.phone)
                    .put("tag", r.tag)
                    .put("noteHash", JSONObject.NULL)
                    .put("createdAt", r.createdAt)
                    .put("clientId", clientId),
            )
        }
        return JSONObject()
            .put("schema", SCHEMA)
            .put("appVersion", appVersion)
            .put("reports", arr)
            .toString(2)
    }
}
