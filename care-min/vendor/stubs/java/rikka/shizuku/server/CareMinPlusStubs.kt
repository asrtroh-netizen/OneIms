package rikka.shizuku.server

import af.shizuku.server.IAIAutomationBridge
import af.shizuku.server.IAICorePlus
import af.shizuku.server.IActivityManagerPlus
import af.shizuku.server.IContinuityBridge
import af.shizuku.server.INetworkGovernorPlus
import af.shizuku.server.IOverlayManagerPlus
import af.shizuku.server.IStorageProxy
import af.shizuku.server.IVirtualMachineManager
import af.shizuku.server.IWindowManagerPlus
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor

/**
 * CARE_MIN 编译桩：满足 [ShizukuService] 对 Plus 扩展面的符号依赖。
 * 不从邻仓复制 *PlusImpl；MVP 不暴露这些能力，全部 no-op / null。
 */
class VirtualMachineManagerImpl : IVirtualMachineManager.Stub() {
    override fun create(name: String?, config: Bundle?) = false
    override fun start(name: String?) = false
    override fun stop(name: String?) = false
    override fun delete(name: String?) = false
    override fun getStatus(name: String?) = "unsupported"
    override fun list(): MutableList<String> = mutableListOf()
}

class StorageProxyImpl : IStorageProxy.Stub() {
    override fun openFile(path: String?, mode: Int): ParcelFileDescriptor? = null
    override fun exists(path: String?) = false
    override fun listFiles(path: String?): MutableList<String> = mutableListOf()
    override fun delete(path: String?) = false
    override fun getFileInfo(path: String?): Bundle? = null
    override fun mkdir(path: String?) = false
}

class AICorePlusImpl(
    @Suppress("UNUSED_PARAMETER") clientManager: ShizukuClientManager,
    @Suppress("UNUSED_PARAMETER") service: ShizukuService,
) : IAICorePlus.Stub() {
    fun setAutomationBridge(@Suppress("UNUSED_PARAMETER") bridge: IAIAutomationBridge?) {}
    override fun getPixelColor(x: Int, y: Int) = 0
    override fun scheduleNPULoad(taskData: Bundle?): Bundle? = null
    override fun captureLayer(layerId: Int): Bitmap? = null
    override fun getSystemContext(): Bundle? = null
    override fun simulateTouch(x: Float, y: Float) = false
    override fun simulateSwipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Int) = false
    override fun simulateText(text: String?) = false
    override fun getWindowHierarchy() = ""
    override fun getServerStats(): Bundle? = null
}

class WindowManagerPlusImpl : IWindowManagerPlus.Stub() {
    override fun forceResizable(packageName: String?, enabled: Boolean) {}
    override fun pinToRegion(taskId: Int, region: Rect?) {}
    override fun setAsBubble(taskId: Int, enabled: Boolean) {}
    override fun configureBubbleBar(settings: Bundle?) {}
    override fun setAlwaysOnTop(taskId: Int, enabled: Boolean) {}
    override fun setImmersiveMode(enabled: Boolean) {}
    override fun setDexHighRefreshRate(enabled: Boolean) {}
    override fun getVisibleWindows(): Bundle? = null
}

class ContinuityBridgeImpl : IContinuityBridge.Stub() {
    override fun syncData(targetDeviceId: String?, key: String?, data: Bundle?) = false
    override fun registerContinuityListener(listener: IBinder?) {}
    override fun listEligibleDevices(): MutableList<String> = mutableListOf()
    override fun requestHandoff(targetDeviceId: String?, taskState: Bundle?) = false
}

class OverlayManagerPlusImpl : IOverlayManagerPlus.Stub() {
    override fun setOverlayEnabled(packageName: String?, enabled: Boolean) = false
    override fun setHighestPriority(packageName: String?) = false
    override fun getAllOverlays(): MutableList<String> = mutableListOf()
    override fun injectResourceOverlay(
        targetPackage: String?,
        resourceName: String?,
        type: Int,
        value: String?,
    ) = false
    override fun prepareShadowMount(callingPackage: String?, partition: String?) = false
}

class NetworkGovernorPlusImpl : INetworkGovernorPlus.Stub() {
    override fun setPrivateDns(mode: String?, hostname: String?) = false
    override fun restrictAppNetwork(packageName: String?, restricted: Boolean) = false
    override fun isAppNetworkRestricted(packageName: String?) = false
}

class ActivityManagerPlusImpl : IActivityManagerPlus.Stub() {
    override fun deepForceStop(packageName: String?) = false
    override fun setAppStandbyBucket(packageName: String?, bucket: Int) = false
    override fun killAllBackgroundProcesses() = false
    override fun freezeApp(packageName: String?) = false
    override fun unfreezeApp(packageName: String?) = false
    override fun isAppFrozen(packageName: String?) = false
    override fun setAppProcessLimit(limit: Int) {}
    override fun getRunningProcesses(): MutableList<String> = mutableListOf()
    override fun clearAppCache(packageName: String?) = false
    override fun clearAppData(packageName: String?) = false
}
