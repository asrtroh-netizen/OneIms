package com.oneims.app.core

import android.content.Context
import android.os.Build
import android.sun.security.x509.AlgorithmId
import android.sun.security.x509.CertificateAlgorithmId
import android.sun.security.x509.CertificateExtensions
import android.sun.security.x509.CertificateIssuerName
import android.sun.security.x509.CertificateSerialNumber
import android.sun.security.x509.CertificateSubjectName
import android.sun.security.x509.CertificateValidity
import android.sun.security.x509.CertificateVersion
import android.sun.security.x509.CertificateX509Key
import android.sun.security.x509.KeyIdentifier
import android.sun.security.x509.PrivateKeyUsageExtension
import android.sun.security.x509.SubjectKeyIdentifierExtension
import android.sun.security.x509.X500Name
import android.sun.security.x509.X509CertImpl
import android.sun.security.x509.X509CertInfo
import android.util.Base64
import android.util.Log
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.Random

/**
 * 方案 B · 原生内嵌 ADB（libadb-android）：本机无线调试配对后执行核心 start.sh。
 */
object OneKukuEmbeddedAdbActivator {

    private const val TAG = "OneIMS-EmbeddedAdb"
    private const val HOST = "127.0.0.1"
    private const val PREFS = "onekuku_adb_identity"
    private const val KEY_PRIVATE = "private_key_b64"
    private const val KEY_CERT = "cert_b64"

    sealed class Outcome {
        data object NeedPairingCode : Outcome()
        data class Success(val detail: String) : Outcome()
        data class Failed(val reason: String) : Outcome()
    }

    suspend fun activate(context: Context, pairingCode: String?): Outcome =
        withContext(Dispatchers.IO) {
            if (!OneKukuCoreComponent.isInstalled(context)) {
                return@withContext Outcome.Failed("core_missing")
            }
            val app = context.applicationContext
            val manager = runCatching { OneKukuAdbConnectionManager.get(app) }
                .getOrElse {
                    Log.w(TAG, "manager init failed", it)
                    return@withContext Outcome.Failed("manager_init")
                }

            val ports = OneKukuAdbMdns.discover(app)
            Log.i(TAG, "mdns pair=${ports.pairPort} connect=${ports.connectPort}")

            val code = pairingCode?.trim().orEmpty()
            if (ports.pairPort != null && code.length >= 6) {
                val paired = runCatching {
                    manager.pair(HOST, ports.pairPort, code)
                    true
                }.getOrElse {
                    Log.w(TAG, "pair failed", it)
                    false
                }
                if (!paired) return@withContext Outcome.Failed("pair_failed")
            } else if (ports.connectPort == null) {
                ShizukuSetupHelper.openWirelessDebugging(app)
                return@withContext if (code.isEmpty()) {
                    Outcome.NeedPairingCode
                } else {
                    Outcome.Failed("ports_missing")
                }
            }

            val connected = runCatching {
                when {
                    ports.connectPort != null -> manager.connect(HOST, ports.connectPort)
                    else -> manager.connectTls(app, 8_000L)
                }
                true
            }.getOrElse {
                Log.w(TAG, "connect failed", it)
                false
            }
            if (!connected) {
                return@withContext if (code.isEmpty()) {
                    Outcome.NeedPairingCode
                } else {
                    Outcome.Failed("connect_failed")
                }
            }

            val startCmd =
                "sh /storage/emulated/0/Android/data/${OneKukuCoreComponent.CORE_PACKAGE}/start.sh\n"
            val shellOk = runCatching {
                manager.openStream("shell:").use { stream ->
                    stream.openOutputStream().use { out ->
                        out.write(startCmd.toByteArray(StandardCharsets.UTF_8))
                        out.flush()
                    }
                    // 短暂读取输出，避免无限阻塞
                    val buf = ByteArray(4096)
                    val input = stream.openInputStream()
                    val read = runCatching {
                        input.read(buf)
                    }.getOrDefault(-1)
                    val text = if (read > 0) {
                        String(buf, 0, read, StandardCharsets.UTF_8)
                    } else {
                        ""
                    }
                    Log.i(TAG, "shell out=$text")
                    true
                }
            }.getOrElse {
                Log.w(TAG, "shell failed", it)
                false
            }
            if (!shellOk) return@withContext Outcome.Failed("start_failed")

            Thread.sleep(800)
            if (OneKukuManager.isRunning()) {
                Outcome.Success("core_running")
            } else {
                Outcome.Success("start_issued")
            }
        }

    /**
     * 持久化 RSA 身份，供 libadb 配对/连接复用。
     */
    internal class OneKukuAdbConnectionManager private constructor(
        private val appContext: Context,
    ) : AbsAdbConnectionManager() {

        private val privateKey: PrivateKey
        private val certificate: Certificate

        init {
            setApi(Build.VERSION.SDK_INT)
            val loaded = loadOrCreateIdentity(appContext)
            privateKey = loaded.first
            certificate = loaded.second
        }

        override fun getPrivateKey(): PrivateKey = privateKey

        override fun getCertificate(): Certificate = certificate

        override fun getDeviceName(): String = "OneKuku"

        companion object {
            @Volatile
            private var instance: OneKukuAdbConnectionManager? = null

            fun get(context: Context): OneKukuAdbConnectionManager {
                val existing = instance
                if (existing != null) return existing
                return synchronized(this) {
                    instance ?: OneKukuAdbConnectionManager(context.applicationContext).also {
                        instance = it
                    }
                }
            }

            private fun loadOrCreateIdentity(context: Context): Pair<PrivateKey, Certificate> {
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val privB64 = prefs.getString(KEY_PRIVATE, null)
                val certB64 = prefs.getString(KEY_CERT, null)
                if (!privB64.isNullOrBlank() && !certB64.isNullOrBlank()) {
                    val keyBytes = Base64.decode(privB64, Base64.DEFAULT)
                    val certBytes = Base64.decode(certB64, Base64.DEFAULT)
                    val key = KeyFactory.getInstance("RSA")
                        .generatePrivate(PKCS8EncodedKeySpec(keyBytes))
                    val cert = CertificateFactory.getInstance("X.509")
                        .generateCertificate(certBytes.inputStream())
                    return key to cert
                }

                val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
                keyPairGenerator.initialize(2048, SecureRandom.getInstance("SHA1PRNG"))
                val keyPair = keyPairGenerator.generateKeyPair()
                val publicKey = keyPair.public
                val private = keyPair.private

                val subject = "CN=OneKuku"
                val algorithmName = "SHA512withRSA"
                val expiryDate = System.currentTimeMillis() + 86400000L * 3650
                val certificateExtensions = CertificateExtensions()
                certificateExtensions.set(
                    "SubjectKeyIdentifier",
                    SubjectKeyIdentifierExtension(KeyIdentifier(publicKey).identifier),
                )
                val x500Name = X500Name(subject)
                val notBefore = Date()
                val notAfter = Date(expiryDate)
                certificateExtensions.set(
                    "PrivateKeyUsage",
                    PrivateKeyUsageExtension(notBefore, notAfter),
                )
                val certificateValidity = CertificateValidity(notBefore, notAfter)
                val x509CertInfo = X509CertInfo()
                x509CertInfo.set("version", CertificateVersion(2))
                x509CertInfo.set(
                    "serialNumber",
                    CertificateSerialNumber(Random().nextInt() and Int.MAX_VALUE),
                )
                x509CertInfo.set(
                    "algorithmID",
                    CertificateAlgorithmId(AlgorithmId.get(algorithmName)),
                )
                x509CertInfo.set("subject", CertificateSubjectName(x500Name))
                x509CertInfo.set("key", CertificateX509Key(publicKey))
                x509CertInfo.set("validity", certificateValidity)
                x509CertInfo.set("issuer", CertificateIssuerName(x500Name))
                x509CertInfo.set("extensions", certificateExtensions)
                val x509CertImpl = X509CertImpl(x509CertInfo)
                x509CertImpl.sign(private, algorithmName)

                prefs.edit()
                    .putString(
                        KEY_PRIVATE,
                        Base64.encodeToString(private.encoded, Base64.DEFAULT),
                    )
                    .putString(
                        KEY_CERT,
                        Base64.encodeToString(x509CertImpl.encoded, Base64.DEFAULT),
                    )
                    .apply()

                return private to x509CertImpl
            }
        }
    }
}

object EmbeddedKadbActivationBridge : OneKukuAdbActivationBridge {
    override fun buildGuideScript(context: Context): String =
        OneKukuCoreComponent.guidedActivationScript(context)

    override fun openWirelessDebugging(context: Context): Boolean =
        ShizukuSetupHelper.openWirelessDebugging(context)
}
