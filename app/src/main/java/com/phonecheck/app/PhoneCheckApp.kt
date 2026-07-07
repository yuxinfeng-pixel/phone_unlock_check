package com.phonecheck.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

class PhoneCheckApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "手机监控服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "监控手机解锁状态，长时间未使用发送提醒"
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "phone_monitor_channel"
        const val PREFS_NAME = "PhoneCheckPrefs"
        const val KEY_PHONE1 = "phone1"
        const val KEY_PHONE2 = "phone2"
        const val KEY_PHONE3 = "phone3"
        const val KEY_LAST_UNLOCK = "last_unlock_time"
        const val KEY_SERVICE_RUNNING = "service_running"
    }
}
