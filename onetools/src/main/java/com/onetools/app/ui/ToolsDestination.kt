package com.onetools.app.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector
import com.onetools.app.R

/** Bottom Dock destinations — aligned with OneIMS island dock pattern. */
enum class ToolsDestination(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    HOME(R.string.tab_home, Icons.Filled.Home),
    CALLER(R.string.tab_caller, Icons.Filled.Call),
    METER(R.string.tab_meter, Icons.Filled.Refresh),
    BATTERY(R.string.tab_battery, Icons.Filled.Star),
    MORE(R.string.tab_more, Icons.Filled.Menu),
}
