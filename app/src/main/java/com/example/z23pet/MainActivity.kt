package com.example.z23pet

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var etUrl: EditText
    private lateinit var etKey: EditText
    private lateinit var etModel: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        etUrl = findViewById(R.id.etUrl)
        etKey = findViewById(R.id.etKey)
        etModel = findViewById(R.id.etModel)
        val sp = getSharedPreferences("z23", MODE_PRIVATE)
        etUrl.setText(sp.getString("api_url", "https://api.openai.com/v1/chat/completions"))
        etKey.setText(sp.getString("api_key", ""))
        etModel.setText(sp.getString("model", "gpt-3.5-turbo"))

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            sp.edit().putString("api_url", etUrl.text.toString().trim())
                .putString("api_key", etKey.text.toString().trim())
                .putString("model", etModel.text.toString().trim()).apply()
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnOverlay).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")))
            } else Toast.makeText(this, "悬浮窗权限已开启", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnStart).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "请先开启悬浮窗权限", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            requestNotifPermission()
            val intent = Intent(this, FloatingPetService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
            else startService(intent)
            Toast.makeText(this, "Z23 出动！", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnStop).setOnClickListener {
            stopService(Intent(this, FloatingPetService::class.java))
            Toast.makeText(this, "Z23 休息了", Toast.LENGTH_SHORT).show()
        }
    }
    private fun requestNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }
}
