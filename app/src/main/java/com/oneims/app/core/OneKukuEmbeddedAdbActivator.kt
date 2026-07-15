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
 * 方案 B · 原生内嵌 ADB（libadb-android）：本机无线调试配对后拉起 OneBridge。
 * Success 仅在 binder 已送达（[OneKukuManager.isRunning]）时返回，禁止 start_issued 假绿。
 */
object OneKukuEmbeddedAdbActivator {

    private const val TAG = "OneIMS-EmbeddedAdb"
    private const val HOST = "127.0.0.1"
    private const val PERSIST_PORT = 5555
    private const val PREFS = "onekuku_adb_identity"
    private const val KEY_PRIVATE = "private_key_b64"
    private const val KEY_CERT = "cert_b64"
    private const val BINDER_WAIT_MS = 5_000L
    private const val BINDER_POLL_MS = 400L

    sealed class Outcome {
        data object NeedPairingCode : Outcome()
        data class Success(val detail: String) : Outcome()
        data class Failed(val reason: String) : Outcome()
    }

    /** 解析 shell 启动输出：必须见到 started 标记，且不得含 missing。 */
    internal fun isShellBootOutputOk(output: String): Boolean {
        val text = output.trim()
        if (text.contains("OneBridge_missing", ignoreCase = false)) return false
        return text.contains("OneBridge_started")
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

            // Pixel 等机型：只开个人热点、未以 STA 连上 Wi‑Fi 时 tls_port=0，mDNS 扫不到端口。
            // 这时弹「要配对码」会误导——系统根本出不了可用的无线调试端口。
            if (ports.pairPort == null && ports.connectPort == null &&
                !OneKukuAdbMdns.isWifiClientConnected(app)
            ) {
                return@withContext Outcome.Failed("wifi_sta_required")
            }

            // 系统已弹出配对页（能扫到 pair 端口）但用户还没填码 → 立刻要码，别先瞎连。
            if (code.length < 6 && ports.pairPort != null) {
                return@withContext Outcome.NeedPairingCode
            }

            if (ports.pairPort != null && code.length >= 6) {
                val paired = runCatching {
                    manager.pair(HOST, ports.pairPort, code)
                    true
                }.getOrElse {
                    Log.w(TAG, "pair failed", it)
                    false
                }
                if (!paired) return@withContext Outcome.Failed("pair_failed")
            } else if (code.length >= 6 && ports.pairPort == null) {
                // 用户填了码，但系统配对页已关掉 / mDNS 没扫到
                return@withContext Outcome.Failed("pair_port_missing")
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
                // 未配对过的本机身份连不上时，回到要码；已填码则报失败。
                return@withContext if (code.length < 6) {
                    Outcome.NeedPairingCode
                } else {
                    Outcome.Failed("connect_failed")
                }
            }

            // 持久化：无线调试跳板 → tcpip 5555 → 回环自连（出门关 WiFi 也可保活）
            val persisted = persistTcpip5555(manager)
            Log.i(TAG, "tcpip5555 persisted=$persisted")

            val startPkg = OneKukuCoreComponent.resolveCorePackage(app)
                ?: OneKukuCoreComponent.BRIDGE_PACKAGE
            val startCmd = OneKukuCoreComponent.bridgeBootShellCommand(startPkg) + "\n"
            val shellOk = runCatching {
                writeShell(manager, startCmd)
            }.getOrElse {
                Log.w(TAG, "shell failed", it)
                false
            }
            if (!shellOk) return@withContext Outcome.Failed("start_failed")

            if (!awaitBinderRunning()) {
                return@withContext Outcome.Failed("binder_not_received")
            }
            Outcome.Success(if (persisted) "core_running_tcpip" else "core_running")
        }

    private fun awaitBinderRunning(): Boolean {
        val deadline = System.currentTimeMillis() + BINDER_WAIT_MS
        while (System.currentTimeMillis() < deadline) {
            if (OneKukuManager.isRunning()) return true
            Thread.sleep(BINDER_POLL_MS)
        }
        return OneKukuManager.isRunning()
    }

    /**
     * 通过 ADB 服务 `tcpip:5555` 把 adbd 切到固定端口，再连回 127.0.0.1:5555。
     * 失败不阻断后续 start.sh（仍可用当前无线调试连接）。
     */
    private fun persistTcpip5555(manager: AbsAdbConnectionManager): Boolean {
        val switched = runCatching {
            manager.openStream("tcpip:5555").use { stream ->
                val buf = ByteArray(64)
                runCatching { stream.openInputStream().read(buf) }
            }
            true
        }.getOrElse {
            Log.w(TAG, "tcpip:5555 failed", it)
            false
        }
        if (!switched) return false
        Thread.sleep(600)
        return runCatching {
            manager.connect(HOST, PERSIST_PORT)
            true
        }.getOrElse {
            Log.w(TAG, "reconnect :$PERSIST_PORT failed", it)
            false
        }
    }

    private fun writeShell(manager: AbsAdbConnectionManager, command: String): Boolean =
        manager.openStream("shell:").use { stream ->
            stream.openOutputStream().use { out ->
                out.write(command.toByteArray(StandardCharsets.UTF_8))
                out.flush()
            }
            val input = stream.openInputStream()
            val buf = ByteArray(4096)
            val collected = StringBuilder()
            val readDeadline = System.currentTimeMillis() + 4_000L
            while (System.currentTimeMillis() < readDeadline) {
                val available = runCatching { input.available() }.getOrDefault(0)
                if (available > 0) {
                    val n = input.read(buf, 0, minOf(buf.size, available))
                    if (n > 0) collected.append(String(buf, 0, n, StandardCharsets.UTF_8))
                    if (isShellBootOutputOk(collected.toString()) ||
                        collected.contains("OneBridge_missing")
                    ) {
                        break
                    }
                } else if (collected.contains("OneBridge_started") ||
                    collected.contains("OneBridge_missing")
                ) {
                    break
                } else {
                    Thread.sleep(50)
                }
            }
            // 尾读一次，兼容部分机型 available() 始终为 0
            if (collected.isEmpty()) {
                val n = runCatching { input.read(buf) }.getOrDefault(-1)
                if (n > 0) collected.append(String(buf, 0, n, StandardCharsets.UTF_8))
            }
            val text = collected.toString()
            Log.i(TAG, "shell out=$text")
            isShellBootOutputOk(text)
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
