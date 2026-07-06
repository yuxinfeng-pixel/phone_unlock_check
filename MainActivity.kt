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
import com.phonecheck.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val prefs by lazy { getSharedPreferences(PhoneCheckApp.PREFS_NAME, MODE_PRIVATE) }

    private val permissions = mutableListOf<String>().apply {
        add(Manifest.permission.SEND_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 加载已保存的电话号码
        loadSavedNumbers()

        // 检查并请求权限
        checkPermissions()

        // 保存按钮
        binding.btnSave.setOnClickListener {
            saveNumbers()
        }

        // 启动/停止服务按钮
        binding.btnToggleService.setOnClickListener {
            toggleService()
        }

        updateServiceButtonState()
    }

    private fun loadSavedNumbers() {
        binding.etPhone1.setText(prefs.getString(PhoneCheckApp.KEY_PHONE1, ""))
        binding.etPhone2.setText(prefs.getString(PhoneCheckApp.KEY_PHONE2, ""))
        binding.etPhone3.setText(prefs.getString(PhoneCheckApp.KEY_PHONE3, ""))
    }

    private fun saveNumbers() {
        val phone1 = binding.etPhone1.text.toString().trim()
        val phone2 = binding.etPhone2.text.toString().trim()
        val phone3 = binding.etPhone3.text.toString().trim()

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
            // 停止服务
            stopService(Intent(this, MonitorService::class.java))
            prefs.edit().putBoolean(PhoneCheckApp.KEY_SERVICE_RUNNING, false).apply()
            Toast.makeText(this, "监控服务已停止", Toast.LENGTH_SHORT).show()
        } else {
            // 检查是否有号码
            val hasNumbers = listOf(
                prefs.getString(PhoneCheckApp.KEY_PHONE1, ""),
                prefs.getString(PhoneCheckApp.KEY_PHONE2, ""),
                prefs.getString(PhoneCheckApp.KEY_PHONE3, "")
            ).any { !it.isNullOrEmpty() }

            if (!hasNumbers) {
                Toast.makeText(this, "请先保存至少一个电话号码", Toast.LENGTH_SHORT).show()
                return
            }

            // 启动服务
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
        binding.btnToggleService.text = if (isRunning) "停止监控服务" else "启动监控服务"
        binding.tvStatus.text = if (isRunning) "状态：监控中 ⏱️" else "状态：已停止"
    }

    private fun checkPermissions() {
        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), 100)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (!allGranted) {
                AlertDialog.Builder(this)
                    .setTitle("需要权限")
                    .setMessage("发送短信和通知权限是必需的，否则应用无法正常工作。")
                    .setPositiveButton("去设置") { _, _ ->
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.parse("package:$packageName")
                        }
                        startActivity(intent)
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }
    }
}
