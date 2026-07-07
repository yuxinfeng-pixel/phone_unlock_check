package com.phonecheck.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences(PhoneCheckApp.PREFS_NAME, MODE_PRIVATE) }

    private val permissions = mutableListOf<String>().apply {
        add(Manifest.permission.SEND_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        loadSavedNumbers()
        checkPermissions()

        findViewById<android.widget.Button>(R.id.btnSave).setOnClickListener { saveNumbers() }
        findViewById<android.widget.Button>(R.id.btnToggleService).setOnClickListener { toggleService() }
        updateServiceButtonState()
    }

    private fun loadSavedNumbers() {
        findViewById<android.widget.EditText>(R.id.etPhone1).setText(prefs.getString(PhoneCheckApp.KEY_PHONE1, ""))
        findViewById<android.widget.EditText>(R.id.etPhone2).setText(prefs.getString(PhoneCheckApp.KEY_PHONE2, ""))
        findViewById<android.widget.EditText>(R.id.etPhone3).setText(prefs.getString(PhoneCheckApp.KEY_PHONE3, ""))
    }

    private fun saveNumbers() {
        val phone1 = findViewById<android.widget.EditText>(R.id.etPhone1).text.toString().trim()
        val phone2 = findViewById<android.widget.EditText>(R.id.etPhone2).text.toString().trim()
        val phone3 = findViewById<android.widget.EditText>(R.id.etPhone3).text.toString().trim()

        if (phone1.isEmpty() && phone2.isEmpty() && phone3.isEmpty()) {
            Toast.makeText(this, "请至少输入一个电话号码", Toast.LENGTH_SHORT).show()
            return
        }

        prefs.edit().apply {
            putString(PhoneCheckApp.KEY_PHONE1, phone1)
            putString(PhoneCheckApp.KEY_PHONE2, phone2)
            putString(PhoneCheckApp.KEY_PHONE3, phone3)
            apply()
        }
        Toast.makeText(this, "电话号码已保存", Toast.LENGTH_SHORT).show()
    }

    private fun toggleService() {
        val isRunning = prefs.getBoolean(PhoneCheckApp.KEY_SERVICE_RUNNING, false)
        if (isRunning) {
            stopService(Intent(this, MonitorService::class.java))
            prefs.edit().putBoolean(PhoneCheckApp.KEY_SERVICE_RUNNING, false).apply()
            Toast.makeText(this, "监控服务已停止", Toast.LENGTH_SHORT).show()
        } else {
            val hasNumbers = listOf(
                prefs.getString(PhoneCheckApp.KEY_PHONE1, ""),
                prefs.getString(PhoneCheckApp.KEY_PHONE2, ""),
                prefs.getString(PhoneCheckApp.KEY_PHONE3, "")
            ).any { !it.isNullOrEmpty() }

            if (!hasNumbers) {
                Toast.makeText(this, "请先保存至少一个电话号码", Toast.LENGTH_SHORT).show()
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(Intent(this, MonitorService::class.java))
            } else {
                startService(Intent(this, MonitorService::class.java))
            }
            prefs.edit().putBoolean(PhoneCheckApp.KEY_SERVICE_RUNNING, true).apply()
            Toast.makeText(this, "监控服务已启动", Toast.LENGTH_SHORT).show()
        }
        updateServiceButtonState()
    }

    private fun updateServiceButtonState() {
        val isRunning = prefs.getBoolean(PhoneCheckApp.KEY_SERVICE_RUNNING, false)
        findViewById<android.widget.Button>(R.id.btnToggleService).text = if (isRunning) "停止监控服务" else "启动监控服务"
        findViewById<android.widget.TextView>(R.id.tvStatus).text = if (isRunning) "状态：监控中 ⏱️" else "状态：已停止"
    }

    private fun checkPermissions() {
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 100)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.any { it != PackageManager.PERMISSION_GRANTED }) {
            AlertDialog.Builder(this)
                .setTitle("需要权限")
                .setMessage("发送短信和通知权限是必需的。")
                .setPositiveButton("去设置") { _, _ ->
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.parse("package:$packageName")
                    })
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }
}
