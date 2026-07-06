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
    private val checkInterval = 60000L // 每分钟检查一次

    // 标记今天是否已经发送过短信（每天只发一次）
    private var lastAlertDate: String = ""

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    // 屏幕亮起，更新最后活动时间
                    updateLastUnlockTime()
                    Log.d("MonitorService", "屏幕亮起，更新时间")
                }
                Intent.ACTION_USER_PRESENT -> {
                    // 用户解锁手机
                    updateLastUnlockTime()
                    Log.d("MonitorService", "用户解锁，更新时间")
                }
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
        
        // 获取唤醒锁，防止系统休眠导致服务停止
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "PhoneCheck::MonitorWakeLock"
        ).apply {
            acquire(10 * 60 * 1000L) // 10分钟超时
        }

        // 注册屏幕状态广播
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)

        // 初始化最后解锁时间为当前时间
        if (prefs.getLong(PhoneCheckApp.KEY_LAST_UNLOCK, 0L) == 0L) {
            updateLastUnlockTime()
        }

        // 启动定时检查
        handler.post(checkRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, createNotification())
        return START_STICKY // 如果被杀死，系统会尝试重启服务
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
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
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        
        // 检查是否在 8:00 - 22:00 之间
        if (hour < 8 || hour >= 22) {
            Log.d("MonitorService", "不在监控时间段内: $hour:00")
            return
        }

        val lastUnlock = prefs.getLong(PhoneCheckApp.KEY_LAST_UNLOCK, System.currentTimeMillis())
        val now = System.currentTimeMillis()
        val diffMinutes = TimeUnit.MILLISECONDS.toMinutes(now - lastUnlock)

        Log.d("MonitorService", "距离上次解锁: $diffMinutes 分钟")

        // 超过1小时未解锁
        if (diffMinutes >= 60) {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            
            // 今天是否已经发送过
            if (lastAlertDate != today) {
                lastAlertDate = today
                sendAlertSms()
            }
        }
    }

    private fun sendAlertSms() {
        val phoneNumbers = listOfNotNull(
            prefs.getString(PhoneCheckApp.KEY_PHONE1, "").takeIf { it.isNotEmpty() },
            prefs.getString(PhoneCheckApp.KEY_PHONE2, "").takeIf { it.isNotEmpty() },
            prefs.getString(PhoneCheckApp.KEY_PHONE3, "").takeIf { it.isNotEmpty() }
        )

        if (phoneNumbers.isEmpty()) {
            Log.w("MonitorService", "没有设置电话号码")
            return
        }

        val message = "【手机状态提醒】该手机已超过1小时未被使用（${getCurrentTime()}），请确认机主安全。"

        phoneNumbers.forEach { phone ->
            try {
                val smsManager = SmsManager.getDefault()
                // 长短信分段
                val parts = smsManager.divideMessage(message)
                smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
                Log.d("MonitorService", "短信已发送至: $phone")
            } catch (e: Exception) {
                Log.e("MonitorService", "发送短信失败: $phone", e)
            }
        }
    }

    private fun getCurrentTime(): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(checkRunnable)
        unregisterReceiver(screenReceiver)
        wakeLock?.release()
        
        // 尝试重启服务
        val restartIntent = Intent(this, MonitorService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(restartIntent)
        } else {
            startService(restartIntent)
        }
    }
}
