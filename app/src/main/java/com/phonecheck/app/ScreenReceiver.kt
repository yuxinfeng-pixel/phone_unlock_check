package com.phonecheck.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ScreenReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                context.sendBroadcast(Intent("com.phonecheck.app.UPDATE_UNLOCK"))
            }
        }
    }
}
