package com.wakewords
import android.app.Activity
import android.content.Intent
import android.os.*
import android.view.Gravity
import android.view.WindowManager
import android.widget.*

class RingActivity : Activity() {
    private val h = Handler(Looper.getMainLooper())
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setShowWhenLocked(true); setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val a = Store.get(this, intent.getIntExtra("id", 0))
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }
        val title = TextView(this).apply { text = a?.label ?: "Wake up"; textSize = 32f; setTextColor(-1); gravity = Gravity.CENTER }
        val vol = TextView(this).apply { textSize = 22f; setTextColor(-1); gravity = Gravity.CENTER }
        fun send(s: String) { startService(Intent(this, RingService::class.java).setAction(s)); finish() }
        val stop = Button(this).apply { text = "STOP"; textSize = 26f; setOnClickListener { send("stop") } }
        val snooze = Button(this).apply { text = "Snooze 5 min"; textSize = 20f; setOnClickListener { send("snooze") } }
        root.addView(title); root.addView(vol); root.addView(stop); root.addView(snooze)
        setContentView(FrameLayout(this).apply {
            addView(WaveBackground(this@RingActivity, 0xFF2B0A12.toInt(), 0xFF8A1C2B.toInt(), 0xFFFFB86B.toInt(), 2.5f))
            addView(root)
        })
        h.post(object : Runnable {
            override fun run() {
                if (!RingService.running) { finish(); return }
                vol.text = "Volume ${RingService.pct}%"; h.postDelayed(this, 500)
            }
        })
    }
    override fun onDestroy() { h.removeCallbacksAndMessages(null); super.onDestroy() }
}
