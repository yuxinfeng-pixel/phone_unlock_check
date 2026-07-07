package com.phonecheck.app

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class MonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences(PhoneCheckApp.PREFS_NAME, MODE_PRIVATE) }
    private var wakeLock: PowerManager.WakeLock? = null
    private val checkInterval = 60000L
    private var lastAlertDate: String = ""

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> updateLastUnlockTime()
            }
        }
    }

    private val checkRunnable = object : Runnable {
        override fun run() {
            checkAndAlert()
            handler.postDelayed(this, checkInterval)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PhoneCheck::MonitorWakeLock")
        wakeLock?.acquire(10 * 60 * 1000L)

        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        })

        if (prefs.getLong(PhoneCheckApp.KEY_LAST_UNLOCK, 0L) == 0L) {
            updateLastUnlockTime()
        }
        handler.post(checkRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, createNotification())
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, PhoneCheckApp.CHANNEL_ID)
            .setContentTitle("手机监控服务运行中")
            .setContentText("正在监控手机解锁状态...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateLastUnlockTime() {
        prefs.edit().putLong(PhoneCheckApp.KEY_LAST_UNLOCK, System.currentTimeMillis()).apply()
    }

    private fun checkAndAlert() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour < 8 || hour >= 22) return

        val lastUnlock = prefs.getLong(PhoneCheckApp.KEY_LAST_UNLOCK, System.currentTimeMillis())
        val diffMinutes = TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - lastUnlock)

        if (diffMinutes >= 60) {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            if (lastAlertDate != today) {
                lastAlertDate = today
                sendAlertSms()
            }
        }
    }

    private fun sendAlertSms() {
        val phones = listOfNotNull(
            prefs.getString(PhoneCheckApp.KEY_PHONE1, "").takeIf { it.isNotEmpty() },
            prefs.getString(PhoneCheckApp.KEY_PHONE2, "").takeIf { it.isNotEmpty() },
            prefs.getString(PhoneCheckApp.KEY_PHONE3, "").takeIf { it.isNotEmpty() }
        )
        if (phones.isEmpty()) return

        val message = "【手机状态提醒】该手机已超过1小时未被使用（${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())}），请确认机主安全。"

        phones.forEach { phone ->
            try {
                SmsManager.getDefault().sendMultipartTextMessage(
                    phone, null, SmsManager.getDefault().divideMessage(message), null, null
                )
                Log.d("MonitorService", "短信已发送至: $phone")
            } catch (e: Exception) {
                Log.e("MonitorService", "发送失败: $phone", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(checkRunnable)
        unregisterReceiver(screenReceiver)
        wakeLock?.release()
        val restartIntent = Intent(this, MonitorService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(restartIntent)
        } else {
            startService(restartIntent)
        }
    }
}
