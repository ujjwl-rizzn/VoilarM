package com.wakewords
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.*
import android.os.*
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.graphics.drawable.GradientDrawable
import android.widget.*
import java.io.File

class MainActivity : Activity() {
    private var tts: TextToSpeech? = null
    private var rec: MediaRecorder? = null
    private var recStart = 0L
    private var cur: File? = null
    private var mp: MediaPlayer? = null
    private val h = Handler(Looper.getMainLooper())
    private lateinit var tp: TimePicker
    private lateinit var label: EditText
    private lateinit var say: EditText
    private lateinit var status: TextView
    private lateinit var fx: Spinner
    private lateinit var daily: CheckBox
    private lateinit var list: LinearLayout
    private lateinit var recBtn: Button
    private val pitches = floatArrayOf(1f, 0.8f, 0.65f, 1.3f, 1.6f)
    private val names = arrayOf("Normal voice", "Deep voice", "Very deep voice", "High voice", "Chipmunk voice")

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        tts = TextToSpeech(this) {}
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 40, 40, 80) }
        fun tv(t: String, s: Float = 16f) = TextView(this).apply { text = t; textSize = s; setPadding(0, 16, 0, 8); setTextColor(0xFFE6F1EE.toInt()) }
        fun btn(t: String, f: () -> Unit) = Button(this).apply {
            text = t; isAllCaps = false; setTextColor(0xFF06121A.toInt()); setOnClickListener { f() }
            background = GradientDrawable().apply { cornerRadius = 48f; setColor(0xFF7CF0E0.toInt()) }
        }
        tp = TimePicker(this); label = EditText(this).apply { hint = "Label (e.g. Gym)"; setTextColor(-1); setHintTextColor(0xFF8FA6A0.toInt()) }
        say = EditText(this).apply { hint = "Type what the AI voice should say"; setTextColor(-1); setHintTextColor(0xFF8FA6A0.toInt()) }
        status = tv("Step 1: allow permissions. Step 2: record or type your wake-up words.")
        fx = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, names) }
        daily = CheckBox(this).apply { text = "Repeat daily"; isChecked = true; setTextColor(-1) }
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        recBtn = btn("Record my voice (min 15 sec)") { toggleRec() }
        root.addView(tv("VoilarM", 34f).apply { setTextColor(0xFF7CF0E0.toInt()); letterSpacing = 0.12f }); root.addView(tp); root.addView(label)
        root.addView(btn("1. Allow mic & notifications") { perms() })
        root.addView(btn("2. Allow background ringing") { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) })
        root.addView(tv("Alarm voice", 20f)); root.addView(recBtn); root.addView(tv("or")); root.addView(say)
        root.addView(btn("Make AI voice from text") { makeVoice() })
        root.addView(fx); root.addView(btn("Play preview") { preview() }); root.addView(status)
        root.addView(daily); root.addView(btn("Save alarm") { save() })
        root.addView(tv("Your alarms", 20f)); root.addView(list)
        setContentView(FrameLayout(this).apply {
            addView(WaveBackground(this@MainActivity, 0xFF0B1020.toInt(), 0xFF2A1458.toInt(), 0xFF7CF0E0.toInt()))
            addView(ScrollView(this@MainActivity).apply { addView(root) })
        })
        render()
    }

    private fun perms() {
        val p = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) p.add(Manifest.permission.POST_NOTIFICATIONS)
        requestPermissions(p.toTypedArray(), 1)
    }

    private val tick = object : Runnable {
        override fun run() {
            if (rec == null) return
            val s = ((System.currentTimeMillis() - recStart) / 1000).toInt()
            if (s >= 60) { stopRec(); return }
            status.text = "Recording… ${s}s" + if (s < 15) " (minimum 15)" else " – tap Stop when done"
            recBtn.text = if (s < 15) "Recording…" else "Stop recording"
            h.postDelayed(this, 500)
        }
    }

    @Suppress("DEPRECATION")
    private fun toggleRec() {
        if (rec != null) {
            if (System.currentTimeMillis() - recStart < 15000) { status.text = "Keep going – at least 15 seconds."; return }
            stopRec(); return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) { perms(); return }
        val f = File(filesDir, "tmp_rec.m4a")
        try {
            rec = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC); setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC); setOutputFile(f.path); prepare(); start()
            }
            recStart = System.currentTimeMillis(); h.post(tick)
        } catch (e: Exception) { rec = null; status.text = "Could not start recording." }
    }

    private fun stopRec() {
        try { rec?.stop() } catch (e: Exception) {}
        rec?.release(); rec = null
        cur = File(filesDir, "tmp_rec.m4a"); recBtn.text = "Record again"
        status.text = "Recorded. Press Play preview, then Save alarm."
    }

    private fun makeVoice() {
        val t = say.text.toString().trim()
        if (t.isEmpty()) { status.text = "Type a sentence first."; return }
        val f = File(filesDir, "tmp_tts.wav"); f.delete()
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(u: String?) {}
            override fun onDone(u: String?) { runOnUiThread { cur = f; status.text = "AI voice ready. Press Play preview." } }
            override fun onError(u: String?) { runOnUiThread { status.text = "Voice failed. Check your phone's text-to-speech settings." } }
        })
        val r = tts?.synthesizeToFile(t, Bundle(), f, "u")
        if (r != TextToSpeech.SUCCESS) status.text = "Text-to-speech is not ready yet. Try again."
    }

    private fun preview() {
        val f = cur ?: run { status.text = "Record or make a voice first."; return }
        mp?.release()
        mp = MediaPlayer().apply { setDataSource(f.path); prepare(); start() }
        try { mp?.playbackParams = PlaybackParams().setPitch(pitches[fx.selectedItemPosition]).setSpeed(1f) } catch (e: Exception) {}
    }

    private fun save() {
        val src = cur
        if (src == null || !src.exists()) { status.text = "Record or make a voice first."; return }
        val id = (System.currentTimeMillis() / 1000).toInt()
        val dest = File(filesDir, "a$id.${src.extension}"); src.copyTo(dest, true)
        val a = Alarm(id, tp.hour, tp.minute, label.text.toString().ifBlank { "Wake up" }, dest.path,
            pitches[fx.selectedItemPosition], daily.isChecked, true)
        val l = Store.all(this); l.add(a); Store.save(this, l); Sched.set(this, a)
        status.text = "Alarm saved."; render()
    }

    private fun render() {
        val ctx = applicationContext
        list.removeAllViews()
        Store.all(ctx).sortedBy { it.hour * 60 + it.minute }.forEach { a ->
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            val t = TextView(this).apply {
                text = String.format("%02d:%02d  %s%s", a.hour, a.minute, a.label, if (a.daily) " (daily)" else "")
                textSize = 18f; setTextColor(-1); layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            val sw = Switch(this).apply {
                isChecked = a.on
                setOnCheckedChangeListener { _, v ->
                    Store.setOn(ctx, a.id, v); if (v) Sched.set(ctx, a) else Sched.cancel(ctx, a.id)
                }
            }
            val del = Button(this).apply {
                text = "X"
                setOnClickListener {
                    Sched.cancel(ctx, a.id); File(a.file).delete()
                    Store.save(ctx, Store.all(ctx).filter { it.id != a.id }); render()
                }
            }
            row.addView(t); row.addView(sw); row.addView(del); list.addView(row)
        }
    }

    override fun onDestroy() { tts?.shutdown(); mp?.release(); super.onDestroy() }
}
