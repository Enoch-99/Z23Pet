package com.example.z23pet

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class FloatingPetService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var root: LinearLayout
    private lateinit var pet: ImageView
    private lateinit var bubble: TextView
    private lateinit var params: WindowManager.LayoutParams
    private val handler = Handler(Looper.getMainLooper())
    private val lines = listOf("Guten Morgen!", "指挥官，别偷懒！", "今天也要好好工作哦。",
        "Z23，随时待命。", "要摸摸头吗？", "文件整理好了吗？")

    override fun onCreate() {
        super.onCreate()
        createChannel()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, buildNotification())
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        bubble = TextView(this).apply {
            setBackgroundColor(0xE6FFFFFF.toInt()); setTextColor(0xFF222222.toInt())
            textSize = 14f; setPadding(24, 16, 24, 16); visibility = View.GONE
        }
        val size = (120 * resources.displayMetrics.density).toInt()
        pet = ImageView(this).apply { setImageResource(R.drawable.z23_idle); layoutParams = LinearLayout.LayoutParams(size, size) }
        root.addView(bubble); root.addView(pet)
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 200; y = 500 }
        wm.addView(root, params); setupTouch()
    }
    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel("z23", "Z23 桌宠", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
    }
    private fun buildNotification(): Notification {
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, "z23")
        else Notification.Builder(this)
        return b.setContentTitle("Z23 桌宠运行中").setContentText("点我互动，拖动我换位置")
            .setSmallIcon(android.R.drawable.ic_menu_compass).build()
    }
    private fun setupTouch() {
        pet.setOnTouchListener(object : View.OnTouchListener {
            private var initX = 0; private var initY = 0
            private var downX = 0f; private var downY = 0f; private var moved = false
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { initX = params.x; initY = params.y; downX = e.rawX; downY = e.rawY; moved = false; return true }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = e.rawX - downX; val dy = e.rawY - downY
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) moved = true
                        params.x = initX + dx.toInt(); params.y = initY + dy.toInt()
                        wm.updateViewLayout(root, params); return true
                    }
                    MotionEvent.ACTION_UP -> { if (!moved) onPetClick(); return true }
                }
                return false
            }
        })
    }
    private fun onPetClick() {
        val sp = getSharedPreferences("z23", MODE_PRIVATE)
        val key = sp.getString("api_key", "") ?: ""
        if (key.isBlank()) showBubble(lines.random()) else { showBubble("..."); thread { requestAI() } }
    }
    private fun requestAI() {
        val sp = getSharedPreferences("z23", MODE_PRIVATE)
        val urlStr = sp.getString("api_url", "https://api.openai.com/v1/chat/completions")!!
        val key = sp.getString("api_key", "")!!; val model = sp.getString("model", "gpt-3.5-turbo")!!
        try {
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"; conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $key"); conn.doOutput = true
            conn.connectTimeout = 15000; conn.readTimeout = 30000
            val body = JSONObject().apply {
                put("model", model); put("messages", JSONArray().apply {
                    put(JSONObject().apply { put("role", "user")
                        put("content", "你是碧蓝航线里的Z23，用简短、傲娇但认真的语气回复指挥官。") })
                })
            }.toString()
            OutputStreamWriter(conn.outputStream).use { it.write(body) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = BufferedReader(InputStreamReader(stream)).readText()
            val reply = if (code in 200..299) JSONObject(text).getJSONArray("choices")
                .getJSONObject(0).getJSONObject("message").getString("content") else "连接失败：$code"
            handler.post { showBubble(reply.take(120)) }
        } catch (e: Exception) { handler.post { showBubble("出错啦：${e.message}") } }
    }
    private fun showBubble(text: String) {
        bubble.text = text; bubble.visibility = View.VISIBLE
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ bubble.visibility = View.GONE }, 5000)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        return START_STICKY
    }
    override fun onDestroy() { super.onDestroy(); if (::root.isInitialized) wm.removeView(root) }
    override fun onBind(intent: Intent?): IBinder? = null
}
