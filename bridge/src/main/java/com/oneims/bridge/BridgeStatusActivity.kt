package com.oneims.bridge

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 安装器「打开」会进到这里。空白页易让用户以为装坏了；
 * 明确引导回 OneIMS 继续「启动通道」。
 */
class BridgeStatusActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BridgeStarter.installStartScript(application)

        val pad = (24 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.WHITE)
        }

        root.addView(
            TextView(this).apply {
                text = getString(R.string.status_hint)
                setTextColor(Color.parseColor("#1A1A1A"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setLineSpacing(0f, 1.2f)
            },
        )

        root.addView(
            Button(this).apply {
                text = getString(R.string.open_oneims)
                setOnClickListener {
                    val launch = packageManager.getLaunchIntentForPackage("com.oneims.app")
                        ?: Intent().setClassName("com.oneims.app", "com.oneims.app.MainActivity")
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    runCatching { startActivity(launch) }
                    finish()
                }
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = pad
                gravity = Gravity.CENTER_HORIZONTAL
            },
        )

        setContentView(root)
    }
}
