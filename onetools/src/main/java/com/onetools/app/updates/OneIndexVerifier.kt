package com.onetools.app.updates

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * ECDSA P-256 (SHA256withECDSA) verification for One Index.
 * Public keys live in assets/one-index-keys.json (keyId → SPKI base64).
 */
object OneIndexVerifier {
    const val ALG = "SHA256withECDSA"

    fun canonicalPayload(doc: JSONObject): ByteArray {
        val copy = JSONObject(doc.toString())
        copy.remove("signature")
        copy.remove("sigAlg")
        copy.remove("keyId")
        return canonicalize(copy).toByteArray(Charsets.UTF_8)
    }

    fun canonicalize(value: Any?): String {
        return when (value) {
            null, JSONObject.NULL -> "null"
            is JSONObject -> {
                val keys = value.keys().asSequence().toList().sorted()
                keys.joinToString(",", "{", "}") { k ->
                    JSONObject.quote(k) + ":" + canonicalize(value.get(k))
                }
            }
            is JSONArray -> {
                buildString {
                    append('[')
                    for (i in 0 until value.length()) {
                        if (i > 0) append(',')
                        append(canonicalize(value.get(i)))
                    }
                    append(']')
                }
            }
            is Number -> {
                val d = value.toDouble()
                if (value is Double || value is Float) {
                    value.toString()
                } else if (d % 1.0 == 0.0) {
                    value.toLong().toString()
                } else {
                    value.toString()
                }
            }
            is Boolean -> value.toString()
            else -> JSONObject.quote(value.toString())
        }
    }

    fun verifyDocument(
        doc: JSONObject,
        publicKeys: Map<String, String>,
        requireSignature: Boolean,
    ): Result<Unit> = runCatching {
        val signatureB64 = doc.optString("signature")
        if (signatureB64.isBlank()) {
            require(!requireSignature) { "索引缺少 signature（已强制验签）" }
            return@runCatching
        }
        val keyId = doc.optString("keyId").ifBlank { error("索引缺少 keyId") }
        val alg = doc.optString("sigAlg", ALG)
        require(alg == ALG) { "不支持的 sigAlg: $alg" }
        val pubB64 = publicKeys[keyId] ?: error("未知 keyId: $keyId（未内置公钥）")
        val keyBytes = Base64.getDecoder().decode(pubB64)
        val publicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(keyBytes))
        val sig = Signature.getInstance(ALG)
        sig.initVerify(publicKey)
        sig.update(canonicalPayload(doc))
        val ok = sig.verify(Base64.getDecoder().decode(signatureB64))
        require(ok) { "索引签名校验失败（可能被篡改）" }
    }

    fun loadPublicKeys(context: Context): Map<String, String> {
        val text = context.assets.open("one-index-keys.json").bufferedReader().readText()
        return parsePublicKeys(text)
    }

    fun parsePublicKeys(raw: String): Map<String, String> {
        val map = JSONObject(raw)
        return buildMap {
            map.keys().forEach { k -> put(k, map.getString(k)) }
        }
    }

    fun verify(context: Context, doc: JSONObject, requireSignature: Boolean): Result<Unit> =
        verifyDocument(doc, loadPublicKeys(context), requireSignature)
}
