package com.phonecheck.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ScreenReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_ON -> {
                Log.d("ScreenReceiver", "屏幕亮起")
            }
            Intent.ACTION_USER_PRESENT -> {
                Log.d("ScreenReceiver", "用户解锁")
                // 通知服务更新最后解锁时间
                context.sendBroadcast(Intent("com.phonecheck.app.UPDATE_UNLOCK"))
            }
        }
    }
}
