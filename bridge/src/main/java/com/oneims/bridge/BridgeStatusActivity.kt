package com.oneims.bridge

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class BridgeStatusActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BridgeStarter.installStartScript(application)
        val tv = TextView(this).apply {
            text = getString(R.string.status_hint)
            setPadding(48, 48, 48, 48)
            textSize = 15f
        }
        setContentView(tv)
    }
}
