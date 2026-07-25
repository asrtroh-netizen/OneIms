package com.onebattery.app.battery

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.onebattery.app.MainActivity
import com.onebattery.app.R

object BatteryWidgetUpdater {
    const val EXTRA_OPEN_BATTERY = "com.onebattery.app.extra.OPEN_BATTERY"

    fun updateAll(context: Context) {
        val app = context.applicationContext
        val mgr = AppWidgetManager.getInstance(app)
        val ids = mgr.getAppWidgetIds(ComponentName(app, BatteryWidgetProvider::class.java))
        if (ids.isEmpty()) return
        update(app, mgr, ids)
    }

    fun update(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        val snap = BatteryReader.read(context) ?: return

        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_BATTERY, true)
        }
        val pending = PendingIntent.getActivity(
            context,
            7103,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.battery_widget)
            views.setTextViewText(R.id.widget_percent, "${snap.percent}%")
            views.setTextViewText(R.id.widget_status, snap.statusLabel)
            views.setTextViewText(
                R.id.widget_meta,
                buildString {
                    append(String.format("%.1f°C", snap.temperatureC))
                    if (snap.currentNowMa > 0) {
                        append(" · ")
                        append(snap.currentNowMa)
                        append(" mA")
                    }
                    if (snap.isPlugged) {
                        append(" · ")
                        append(snap.pluggedLabel)
                    }
                },
            )
            views.setOnClickPendingIntent(R.id.widget_root, pending)
            mgr.updateAppWidget(id, views)
        }
    }
}
