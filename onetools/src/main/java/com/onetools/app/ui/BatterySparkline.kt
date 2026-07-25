package com.onetools.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun BatterySparkline(
    percents: List<Int>,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        drawRect(track)
        if (percents.size < 2) return@Canvas
        val minP = 0f
        val maxP = 100f
        val path = Path()
        percents.forEachIndexed { i, p ->
            val x = size.width * i / (percents.size - 1).coerceAtLeast(1)
            val y = size.height * (1f - ((p - minP) / (maxP - minP)).coerceIn(0f, 1f))
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 3f, cap = StrokeCap.Round),
        )
        // end point
        val last = percents.last()
        val lx = size.width
        val ly = size.height * (1f - (last / 100f).coerceIn(0f, 1f))
        drawCircle(color = color, radius = 4f, center = Offset(lx, ly))
    }
}
