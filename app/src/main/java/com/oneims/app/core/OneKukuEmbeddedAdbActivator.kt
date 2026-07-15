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
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.Random
import kotlin.coroutines.resume

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
    /** 曾经成功配对/拉起过：重启后走「无码直连优先」；从未成功则走正常要码。 */
    private const val KEY_HAS_PAIRED = "has_paired_once"
    private const val BINDER_WAIT_MS = 12_000L
    private const val BINDER_POLL_MS = 300L
    /** pair() 在部分机型上会无限阻塞；独立线程 + 超时，避免通知栏「配对中」卡死。 */
    private const val PAIR_TIMEOUT_MS = 12_000L
    private const val POST_PAIR_DISCOVER_MS = 3_000L
    /** 已配对设备：等系统连上已记住的 Wi‑Fi（前台重开通常已在线，很少走到上限）。 */
    private const val PAIRED_WIFI_WAIT_MS = 12_000L
    private const val PAIRED_CONNECT_RETRIES = 2
    private const val PAIRED_RETRY_GAP_MS = 1_000L
    /** 已配对首轮 mDNS：不必空等满 6s；端口晚到靠第 2 轮补扫。 */
    private const val PAIRED_DISCOVER_MS = 3_000L
    private const val CONNECT_TLS_TIMEOUT_MS = 4_000L
    private val activateMutex = Mutex()

    sealed class Outcome {
        data object NeedPairingCode : Outcome()
        data class Success(val detail: String) : Outcome()
        data class Failed(val reason: String) : Outcome()
    }

    fun hasPairedOnce(context: Context): Boolean {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_HAS_PAIRED, false)) return true
        // 升级兼容：本地已有 ADB 身份密钥 ≈ 曾经配对成功过
        return !prefs.getString(KEY_PRIVATE, null).isNullOrBlank()
    }

    fun markPairedOnce(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_HAS_PAIRED, true)
            .apply()
    }

    /**
     * 解析 shell 启动输出。
     * 只认「整行等于」状态标记——adb `shell:` 会回显整段命令，旧逻辑用 contains("OneBridge_missing")
     * 会把脚本正文误判成失败并提前打断。
     */
    internal fun isShellBootOutputOk(output: String): Boolean =
        shellBootStatus(output) == ShellBootStatus.OK

    internal enum class ShellBootStatus { OK, MISS, UNKNOWN }

    internal fun shellBootStatus(output: String): ShellBootStatus {
        val lines = output.replace("\r\n", "\n").replace('\r', '\n')
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        // 取最后一次完整状态行（真实 printf 输出），忽略命令回显长行
        val last = lines.lastOrNull {
            it == OneKukuCoreComponent.SHELL_BOOT_OK ||
                it == OneKukuCoreComponent.SHELL_BOOT_MISS ||
                // 兼容旧包日志/测试残留
                it == "OneBridge_started" ||
                it == "OneBridge_missing"
        } ?: return ShellBootStatus.UNKNOWN
        return when (last) {
            OneKukuCoreComponent.SHELL_BOOT_OK, "OneBridge_started" -> ShellBootStatus.OK
            else -> ShellBootStatus.MISS
        }
    }

    suspend fun activate(
        context: Context,
        pairingCode: String?,
        pairPortOverride: Int? = null,
        forceRestart: Boolean = false,
    ): Outcome =
        // 串行化：避免「激活」与「通知栏填码」同时抢同一 AdbConnectionManager 导致假死。
        activateMutex.withLock {
            activateLocked(context, pairingCode, pairPortOverride, forceRestart)
        }

    private suspend fun activateLocked(
        context: Context,
        pairingCode: String?,
        pairPortOverride: Int?,
        forceRestart: Boolean = false,
    ): Outcome =
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

            val code = pairingCode?.trim().orEmpty()
            val pairedBefore = hasPairedOnce(app)

            // Wi‑Fi 前置：已配对无码时先等 STA，再扫 mDNS，避免空扫才发现没网。
            if (pairedBefore && code.length < 6 &&
                !OneKukuAdbMdns.isWifiClientConnected(app)
            ) {
                Log.i(TAG, "paired device: wait Wi‑Fi before mDNS")
                if (!OneKukuAdbMdns.waitForWifiClient(app, PAIRED_WIFI_WAIT_MS)) {
                    return@withContext Outcome.Failed("wifi_sta_required")
                }
            }

            // 已配对快路径：上次 tcpip:5555 若仍在，跳过首轮 mDNS（杀进程重开常见）。
            if (pairedBefore && code.length < 6 &&
                OneKukuAdbMdns.isWifiClientConnected(app)
            ) {
                val fast5555 = runCatching {
                    manager.connect(HOST, PERSIST_PORT)
                    true
                }.getOrElse {
                    Log.i(TAG, "fast :$PERSIST_PORT miss: ${it.message}")
                    false
                }
                if (fast5555) {
                    Log.i(TAG, "fast path :$PERSIST_PORT connected, skip first mDNS")
                    val persisted = persistTcpip5555(manager)
                    Log.i(TAG, "tcpip5555 persisted=$persisted")
                    val startPkg = OneKukuCoreComponent.resolveCorePackage(app)
                        ?: OneKukuCoreComponent.HOST_PACKAGE
                    val startCmd =
                        OneKukuCoreComponent.bridgeBootShellCommand(startPkg, forceRestart) + "\n"
                    val boot = runCatching {
                        writeShellAndAwaitBinder(manager, startCmd)
                    }.getOrElse {
                        Log.w(TAG, "shell/binder failed", it)
                        "start_failed"
                    }
                    if (boot == "ok") {
                        markPairedOnce(app)
                        val granted = grantWriteSecureSettings(manager, app.packageName)
                        val wifiOn = ShizukuSetupHelper.tryEnableAdbWifi(app)
                        Log.i(TAG, "post-boot grantSecure=$granted adbWifi=$wifiOn")
                        return@withContext Outcome.Success(
                            if (persisted) "core_running_tcpip" else "core_running",
                        )
                    }
                    Log.w(TAG, "fast :$PERSIST_PORT shell failed ($boot), fall through mDNS")
                }
            }

            var ports = OneKukuAdbMdns.discover(
                app,
                timeoutMs = if (pairedBefore && code.length < 6) {
                    PAIRED_DISCOVER_MS
                } else {
                    6_000L
                },
            )
            Log.i(
                TAG,
                "mdns pair=${ports.pairPort} connect=${ports.connectPort} " +
                    "override=$pairPortOverride",
            )
            var effectivePairPort = ports.pairPort ?: pairPortOverride?.takeIf { it in 1..65535 }

            // 从未配对且无端口：立刻要 Wi‑Fi（不空等）。
            if (effectivePairPort == null && ports.connectPort == null &&
                !OneKukuAdbMdns.isWifiClientConnected(app)
            ) {
                return@withContext Outcome.Failed("wifi_sta_required")
            }

            // 已填码 → 走 pair；未填码时即使扫到 pairPort 也先试直连。
            if (effectivePairPort != null && code.length >= 6) {
                val paired = pairWithTimeout(manager, effectivePairPort, code)
                if (!paired) return@withContext Outcome.Failed("pair_failed")
                ports = OneKukuAdbMdns.discover(app, timeoutMs = POST_PAIR_DISCOVER_MS)
                Log.i(TAG, "post-pair mdns pair=${ports.pairPort} connect=${ports.connectPort}")
            } else if (code.length >= 6 && effectivePairPort == null) {
                return@withContext Outcome.Failed("pair_port_missing")
            }

            // 双路径：已配对 → 多轮无码直连；从未配对 → 单次尝试后走正常要码。
            val connectAttempts = when {
                code.length >= 6 -> 1
                pairedBefore -> PAIRED_CONNECT_RETRIES
                else -> 1
            }
            var connected = false
            repeat(connectAttempts) { attempt ->
                if (attempt > 0) {
                    Thread.sleep(PAIRED_RETRY_GAP_MS)
                    ports = OneKukuAdbMdns.discover(app, timeoutMs = POST_PAIR_DISCOVER_MS)
                    Log.i(
                        TAG,
                        "retry#$attempt mdns pair=${ports.pairPort} connect=${ports.connectPort}",
                    )
                }
                connected = tryConnectOnce(manager, app, ports.connectPort)
                if (connected) return@repeat
            }
            if (!connected) {
                return@withContext if (code.length < 6) {
                    Log.i(
                        TAG,
                        "connect failed without code pairedBefore=$pairedBefore " +
                            "pairPort=${ports.pairPort} connectPort=${ports.connectPort}",
                    )
                    Outcome.NeedPairingCode
                } else {
                    Outcome.Failed("connect_failed")
                }
            }

            // 持久化：无线调试跳板 → tcpip 5555 → 回环自连（出门关 WiFi 也可保活）
            val persisted = persistTcpip5555(manager)
            Log.i(TAG, "tcpip5555 persisted=$persisted")

            val startPkg = OneKukuCoreComponent.resolveCorePackage(app)
                ?: OneKukuCoreComponent.HOST_PACKAGE
            val startCmd =
                OneKukuCoreComponent.bridgeBootShellCommand(startPkg, forceRestart) + "\n"
            val boot = runCatching {
                writeShellAndAwaitBinder(manager, startCmd)
            }.getOrElse {
                Log.w(TAG, "shell/binder failed", it)
                "start_failed"
            }
            when (boot) {
                "ok" -> {
                    markPairedOnce(app)
                    // 留下 WRITE_SECURE_SETTINGS，供下次开机静默写回 adb_wifi_enabled（Shizuku 同款思路）。
                    val granted = grantWriteSecureSettings(manager, app.packageName)
                    val wifiOn = ShizukuSetupHelper.tryEnableAdbWifi(app)
                    Log.i(TAG, "post-boot grantSecure=$granted adbWifi=$wifiOn")
                    Outcome.Success(if (persisted) "core_running_tcpip" else "core_running")
                }
                else -> Outcome.Failed(boot)
            }
        }

    /** 经已建立的 ADB shell 给本包授予 WRITE_SECURE_SETTINGS（持久，跨重启）。 */
    private fun grantWriteSecureSettings(
        manager: AbsAdbConnectionManager,
        packageName: String,
    ): Boolean =
        runCatching {
            val cmd = "pm grant $packageName android.permission.WRITE_SECURE_SETTINGS\n"
            manager.openStream("shell:").use { stream ->
                stream.openOutputStream().use { out ->
                    out.write(cmd.toByteArray(StandardCharsets.UTF_8))
                    out.flush()
                }
                // 短暂排空，避免部分机型堵住
                val input = stream.openInputStream()
                val buf = ByteArray(256)
                val deadline = System.currentTimeMillis() + 1_500L
                while (System.currentTimeMillis() < deadline) {
                    val n = runCatching {
                        if (input.available() > 0) input.read(buf) else 0
                    }.getOrDefault(0)
                    if (n <= 0) Thread.sleep(40)
                }
            }
            true
        }.getOrElse {
            Log.w(TAG, "pm grant WRITE_SECURE_SETTINGS failed", it)
            false
        }

    /** connect 口 → 5555 → connectTls，任一成功即可。 */
    private fun tryConnectOnce(
        manager: AbsAdbConnectionManager,
        app: Context,
        connectPort: Int?,
    ): Boolean {
        // 已配对场景优先固定口，减少「先等 mDNS 口失败再试 5555」的体感。
        val on5555First = runCatching {
            manager.connect(HOST, PERSIST_PORT)
            true
        }.getOrElse {
            Log.w(TAG, "connect :$PERSIST_PORT failed", it)
            false
        }
        if (on5555First) return true
        if (connectPort != null) {
            val ok = runCatching {
                manager.connect(HOST, connectPort)
                true
            }.getOrElse {
                Log.w(TAG, "connect :$connectPort failed", it)
                false
            }
            if (ok) return true
        }
        return runCatching {
            manager.connectTls(app, CONNECT_TLS_TIMEOUT_MS)
            true
        }.getOrElse {
            Log.w(TAG, "connectTls failed", it)
            false
        }
    }

    /**
     * libadb pair 在 TLS/端口异常时可能长时间不返回；放到独立线程并用超时兜底。
     */
    private suspend fun pairWithTimeout(
        manager: AbsAdbConnectionManager,
        pairPort: Int,
        code: String,
    ): Boolean {
        val done = AtomicBoolean(false)
        val result = withTimeoutOrNull(PAIR_TIMEOUT_MS) {
            kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                thread(name = "onekuku-adb-pair", isDaemon = true) {
                    val ok = runCatching {
                        manager.pair(HOST, pairPort, code)
                        true
                    }.getOrElse {
                        Log.w(TAG, "pair failed port=$pairPort", it)
                        false
                    }
                    if (done.compareAndSet(false, true) && cont.isActive) {
                        cont.resume(ok)
                    }
                }
            }
        }
        if (result == null) {
            Log.w(TAG, "pair timed out after ${PAIR_TIMEOUT_MS}ms port=$pairPort")
            done.set(true)
            return false
        }
        return result
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

    /**
     * 写启动命令 → 读到 started → **在同一 shell 流内**等待 binder。
     * @return `"ok"` / `"start_failed"` / `"binder_not_received"`
     */
    private fun writeShellAndAwaitBinder(
        manager: AbsAdbConnectionManager,
        command: String,
    ): String =
        manager.openStream("shell:").use { stream ->
            stream.openOutputStream().use { out ->
                out.write(command.toByteArray(StandardCharsets.UTF_8))
                out.flush()
            }
            val input = stream.openInputStream()
            val buf = ByteArray(4096)
            val collected = StringBuilder()
            val readDeadline = System.currentTimeMillis() + 8_000L
            while (System.currentTimeMillis() < readDeadline) {
                val available = runCatching { input.available() }.getOrDefault(0)
                if (available > 0) {
                    val n = input.read(buf, 0, minOf(buf.size, available))
                    if (n > 0) collected.append(String(buf, 0, n, StandardCharsets.UTF_8))
                } else {
                    Thread.sleep(40)
                }
                when (shellBootStatus(collected.toString())) {
                    ShellBootStatus.OK, ShellBootStatus.MISS -> break
                    ShellBootStatus.UNKNOWN -> Unit
                }
            }
            // 尾读一次，兼容部分机型 available() 始终为 0
            if (shellBootStatus(collected.toString()) == ShellBootStatus.UNKNOWN) {
                val n = runCatching { input.read(buf) }.getOrDefault(-1)
                if (n > 0) collected.append(String(buf, 0, n, StandardCharsets.UTF_8))
            }
            val text = collected.toString()
            Log.i(TAG, "shell out=$text")
            when (shellBootStatus(text)) {
                ShellBootStatus.OK -> Unit
                ShellBootStatus.MISS -> return@use "start_failed"
                ShellBootStatus.UNKNOWN -> return@use "start_failed"
            }
            if (!awaitBinderRunning()) return@use "binder_not_received"
            "ok"
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
