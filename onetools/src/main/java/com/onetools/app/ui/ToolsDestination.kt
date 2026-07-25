package com.onetools.app.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.onetools.app.R

/** Bottom Dock — OneIMS island pattern; Updates is a first-class tab. */
enum class ToolsDestination(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    HOME(R.string.tab_home, Icons.Filled.Home),
    CALLER(R.string.tab_caller, Icons.Filled.Call),
    METER(R.string.tab_meter, Icons.Filled.Refresh),
    UPDATES(R.string.tab_updates, Icons.AutoMirrored.Filled.Send),
    SETTINGS(R.string.tab_settings, Icons.Filled.Settings),
}
