package com.onetools.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import com.onetools.app.channel.ChannelCardPolicy
import com.onetools.app.channel.ChannelCardState
import com.onetools.app.channel.ShizukuChannel
import com.onetools.app.ui.BatteryScreen
import com.onetools.app.ui.HomeScreen
import com.onetools.app.ui.MeterScreen
import com.onetools.app.ui.theme.OneToolsTheme
import kotlinx.coroutines.delay

private enum class AppRoute { Home, Battery, Meter }

class MainActivity : ComponentActivity() {
    private var serviceReady by mutableStateOf(false)
    private var channelSleeping by mutableStateOf(false)
    private var activating by mutableStateOf(false)
    private var detailToast by mutableStateOf<String?>(null)
    private var route by mutableStateOf(AppRoute.Home)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    private val cardState: ChannelCardState
        get() = when {
            activating && !serviceReady -> ChannelCardState.ACTIVATING
            else -> ChannelCardPolicy.resolve(
                serviceReady = serviceReady,
                isExecuting = false,
                channelSleeping = channelSleeping,
            )
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        refreshChannel()
        ensureNotificationPermission()

        ShizukuChannel.addBinderReceivedListener { refreshChannel() }
        ShizukuChannel.addBinderDeadListener {
            serviceReady = false
            activating = false
        }
        ShizukuChannel.addPermissionResultListener { _, _ ->
            refreshChannel()
            if (serviceReady) activating = false
        }

        setContent {
            OneToolsTheme(dynamicColor = false) {
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_STOP -> {
                                if (serviceReady) channelSleeping = true
                            }
                            Lifecycle.Event.ON_START -> {
                                refreshChannel()
                                if (serviceReady) channelSleeping = false
                            }
                            else -> Unit
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                LaunchedEffect(activating) {
                    if (!activating) return@LaunchedEffect
                    repeat(40) {
                        delay(500)
                        refreshChannel()
                        if (serviceReady) {
                            activating = false
                            channelSleeping = false
                            return@LaunchedEffect
                        }
                    }
                    activating = false
                }

                LaunchedEffect(detailToast) {
                    val msg = detailToast ?: return@LaunchedEffect
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                    detailToast = null
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    when (route) {
                        AppRoute.Home -> HomeScreen(
                            channelState = cardState,
                            onActivateOrCheck = { onPrimaryAction() },
                            onOpenBattery = { route = AppRoute.Battery },
                            onOpenMeter = { route = AppRoute.Meter },
                        )
                        AppRoute.Battery -> BatteryScreen(onBack = { route = AppRoute.Home })
                        AppRoute.Meter -> MeterScreen(onBack = { route = AppRoute.Home })
                    }
                }
            }
        }
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun refreshChannel() {
        serviceReady = ShizukuChannel.isServiceReady()
        if (!serviceReady) channelSleeping = false
    }

    private fun onPrimaryAction() {
        when (cardState) {
            ChannelCardState.INACTIVE -> startActivation()
            ChannelCardState.READY,
            ChannelCardState.SLEEPING,
            -> {
                refreshChannel()
                detailToast = getString(R.string.toast_status_ok)
            }
            ChannelCardState.ACTIVATING -> Unit
        }
    }

    private fun startActivation() {
        activating = true
        channelSleeping = false
        if (!ShizukuChannel.isRunning()) {
            val opened = ShizukuChannel.openShizukuManager(this)
            if (!opened) {
                activating = false
                detailToast = getString(R.string.toast_open_shizuku_fail)
            }
            return
        }
        if (!ShizukuChannel.isGranted()) {
            ShizukuChannel.requestPermission()
        } else {
            refreshChannel()
            activating = false
        }
    }
}
