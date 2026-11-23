package com.wakewords
import android.app.Activity
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.view.Gravity
import android.view.WindowManager
import android.widget.*

class RingActivity : Activity() {
    private val h = Handler(Looper.getMainLooper())
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setShowWhenLocked(true); setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val id = intent.getIntExtra("id", 0)
        if (intent.getBooleanExtra("start", false) && !RingService.running) {
            try { startForegroundService(Intent(this, RingService::class.java).putExtra("id", id)) } catch (e: Exception) {}
        }
        val a = Store.get(this, id)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(24), dp(24), dp(24), dp(24)) }
        val title = TextView(this).apply { text = a?.label ?: "Wake up"; textSize = 36f; setTextColor(-1); gravity = Gravity.CENTER }
        val vol = TextView(this).apply { textSize = 22f; setTextColor(-1); gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(24)) }
        fun big(t: String, fill: Int, ink: Int, hDp: Int, sp: Float, act: String) = Button(this).apply {
            text = t; textSize = sp; isAllCaps = false; setTextColor(ink)
            background = GradientDrawable().apply { cornerRadius = dp(40).toFloat(); setColor(fill) }
            layoutParams = LinearLayout.LayoutParams(-1, dp(hDp)).apply { topMargin = dp(16) }
            setOnClickListener { startService(Intent(this@RingActivity, RingService::class.java).setAction(act)); finish() }
        }
        root.addView(title); root.addView(vol)
        root.addView(big("STOP", 0xFFFFFFFF.toInt(), 0xFF2B0A12.toInt(), 110, 32f, "stop"))
        root.addView(big("Snooze 5 minutes", 0x66FFFFFF, -1, 80, 22f, "snooze"))
        setContentView(FrameLayout(this).apply {
            addView(WaveBackground(this@RingActivity, 0xFF2B0A12.toInt(), 0xFF8A1C2B.toInt(), 0xFFFFB86B.toInt(), 2.5f, 2.5f))
            addView(root)
        })
        h.postDelayed(object : Runnable {
            override fun run() {
                if (!RingService.running) { finish(); return }
                vol.text = "Volume ${RingService.pct}%"; h.postDelayed(this, 500)
            }
        }, 3000)
    }
    override fun onDestroy() { h.removeCallbacksAndMessages(null); super.onDestroy() }
}
