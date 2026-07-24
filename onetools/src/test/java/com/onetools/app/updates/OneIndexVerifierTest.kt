package com.onetools.app.updates

import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OneIndexVerifierTest {
    private fun readSibling(vararg parts: String): String {
        val root = File("src/main/assets")
        val f = parts.fold(root) { acc, p -> File(acc, p) }
        check(f.isFile) { "missing ${f.absolutePath}" }
        return f.readText(Charsets.UTF_8)
    }

    @Test
    fun verifiesPythonSignedSample() {
        val doc = JSONObject(readSibling("sample-one-update.json"))
        val keys = OneIndexVerifier.parsePublicKeys(readSibling("one-index-keys.json"))
        val result = OneIndexVerifier.verifyDocument(doc, keys, requireSignature = true)
        assertTrue(result.exceptionOrNull()?.message ?: "ok", result.isSuccess)
    }

    @Test
    fun rejectsTamperedApps() {
        val doc = JSONObject(readSibling("sample-one-update.json"))
        doc.getJSONArray("apps").getJSONObject(0).put("apkUrl", "https://evil.example/malware.apk")
        val keys = OneIndexVerifier.parsePublicKeys(readSibling("one-index-keys.json"))
        val result = OneIndexVerifier.verifyDocument(doc, keys, requireSignature = true)
        assertTrue(result.isFailure)
    }
}
