package com.wakewords
import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.PlaybackParams
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*
import java.io.File
import java.util.Calendar

class MainActivity : Activity() {
    private val h = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ttsOk = false
    private var rec: MediaRecorder? = null
    private var recStart = 0L
    private var curRec: File? = null
    private var curTts: File? = null
    private var mp: MediaPlayer? = null
    private lateinit var frame: FrameLayout
    private lateinit var status: TextView
    private var recBtn: Button? = null
    private var inEditor = false
    private val asked = mutableSetOf<String>()
    private var eh = 7
    private var em = 0
    private var pm = false
    private var useRec = true
    private var styleIdx = 0
    private var daily = true

    private val BG = 0xFF0B1020.toInt()
    private val TEAL = 0xFF7CF0E0.toInt()
    private val DARK = 0xFF06121A.toInt()
    private val INK = 0xFFEAF2F0.toInt()
    private val MUTE = 0xFF9FB3AE.toInt()
    private val CARD = 0xE6141B2E.toInt()
    private val CARD2 = 0xFF26324A.toInt()
    private val RED = 0xFFE5484D.toInt()
    private val pitches = floatArrayOf(1f, 0.8f, 0.65f, 1.3f, 1.6f)
    private val names = arrayOf("Normal voice", "Deep voice", "Very deep voice", "High voice", "Chipmunk voice")
    private val cur: File? get() = if (useRec) curRec else curTts

    // ---------- small helpers ----------
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
    private fun shape(fill: Int, r: Int = 32) = GradientDrawable().apply { cornerRadius = dp(r).toFloat(); setColor(fill) }
    private fun tv(t: String, sp: Float = 18f, color: Int = INK, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = sp; setTextColor(color); if (bold) typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(6), 0, dp(6))
    }
    private fun btn(t: String, fill: Int = TEAL, ink: Int = DARK, f: () -> Unit) = Button(this).apply {
        text = t; textSize = 20f; isAllCaps = false; setTextColor(ink); minHeight = dp(64); minimumHeight = dp(64)
        background = shape(fill)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
        setOnClickListener { f() }
    }
    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(14), dp(18), dp(14)); background = shape(CARD, 20)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) }
    }
    private fun page(content: View) {
        frame.removeAllViews()
        frame.addView(WaveBackground(this, BG, 0xFF2A1458.toInt(), TEAL))
        frame.addView(ScrollView(this).apply { addView(content) })
    }
    private fun fmt(h24: Int, m: Int) =
        String.format("%d:%02d %s", if (h24 % 12 == 0) 12 else h24 % 12, m, if (h24 >= 12) "PM" else "AM")

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        tts = TextToSpeech(this) { ttsOk = it == TextToSpeech.SUCCESS }
        frame = FrameLayout(this)
        setContentView(frame)
        showHome()
    }

    override fun onResume() { super.onResume(); ensureSetup() }

    // ---------- permissions as pop-ups, one at a time, gone once done ----------
    private fun ask(title: String, body: String, go: () -> Unit) {
        AlertDialog.Builder(this).setTitle(title).setMessage(body).setCancelable(false)
            .setPositiveButton("Continue") { _, _ -> go() }.show()
    }
    private fun ensureSetup() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED && asked.add("n")) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2); return
        }
        val pw = getSystemService(PowerManager::class.java)
        if (!pw.isIgnoringBatteryOptimizations(packageName) && asked.add("b")) {
            ask("Keep your alarm reliable", "Allow VoilarM to run in the background so your alarm rings on time, even when the phone is locked.") {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            }
            return
        }
        if (Build.VERSION.SDK_INT >= 34 && !getSystemService(NotificationManager::class.java).canUseFullScreenIntent() && asked.add("f")) {
            ask("Show alarm on lock screen", "Allow VoilarM to show the alarm screen when your phone is locked.") {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
            }
        }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 2) ensureSetup()
        else if (requestCode == 1 && inEditor && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED)
            status.text = "Microphone allowed. Tap Start recording."
    }

    // ---------- home ----------
    private fun showHome() {
        inEditor = false
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(36), dp(20), dp(40)) }
        root.addView(tv("VoilarM", 34f, TEAL, true).apply { letterSpacing = 0.12f })
        root.addView(tv("Wake up to your own voice", 17f, MUTE))
        val alarms = Store.all(this).sortedBy { it.hour * 60 + it.minute }
        if (alarms.isEmpty()) root.addView(card().apply { addView(tv("No alarms yet.\nTap the button below to make your first one.", 20f)) })
        alarms.forEach { root.addView(alarmCard(it)) }
        root.addView(btn("+  New alarm") { showEditor() })
        if (alarms.isNotEmpty()) root.addView(btn("Test the alarm (rings in 10 seconds)", CARD2, INK) {
            Sched.snooze(this, alarms[0].id, 10000); toast("Test alarm rings in 10 seconds. Lock your phone now to test.")
        })
        root.addView(btn("Alarm not ringing? Read this", CARD2, INK) { helpDialog() })
        page(root)
    }
    private fun alarmCard(a: Alarm): View {
        val c = card()
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        left.addView(tv(fmt(a.hour, a.minute), 40f, INK, true))
        left.addView(tv(a.label + if (a.daily) "  ·  every day" else "  ·  once", 17f, MUTE))
        val sw = Switch(this).apply {
            isChecked = a.on; scaleX = 1.5f; scaleY = 1.5f
            setOnCheckedChangeListener { _, v ->
                Store.setOn(applicationContext, a.id, v)
                if (v) Sched.set(applicationContext, a) else Sched.cancel(applicationContext, a.id)
            }
        }
        row.addView(left); row.addView(sw); c.addView(row)
        c.addView(tv("Delete", 17f, 0xFFFF8A80.toInt()).apply { setPadding(0, dp(14), 0, dp(6)); setOnClickListener { confirmDelete(a) } })
        return c
    }
    private fun confirmDelete(a: Alarm) {
        AlertDialog.Builder(this).setTitle("Delete this alarm?")
            .setPositiveButton("Delete") { _, _ ->
                Sched.cancel(this, a.id); File(a.file).delete()
                Store.save(this, Store.all(this).filter { it.id != a.id }); showHome()
            }.setNegativeButton("Keep", null).show()
    }
    private fun helpDialog() {
        AlertDialog.Builder(this).setTitle("If the alarm does not ring")
            .setMessage("1. Phone Settings > Apps > VoilarM > Battery: choose Unrestricted.\n\n2. Turn on Auto-start / Allow background activity for VoilarM if your phone has it.\n\n3. In the recent apps screen, lock VoilarM. Do not swipe it away.\n\n4. Allow notifications and lock-screen display.\n\n5. Use \"Test the alarm\" to check it works.")
            .setPositiveButton("OK", null).show()
    }

    // ---------- editor ----------
    private fun hold(b: View, rep: Boolean, step: () -> Unit) {
        val r = object : Runnable { override fun run() { step(); h.postDelayed(this, 110) } }
        b.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { v.isPressed = true; step(); if (rep) h.postDelayed(r, 450) }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { v.isPressed = false; h.removeCallbacks(r) }
            }
            true
        }
    }
    private fun col(rep: Boolean, value: () -> String, up: () -> Unit, down: () -> Unit): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(4); marginEnd = dp(4) }
        }
        val t = tv(value(), 48f, INK, true).apply { gravity = Gravity.CENTER }
        val u = btn("▲", CARD2, TEAL) {}.also { hold(it, rep) { up(); t.text = value() } }
        val d = btn("▼", CARD2, TEAL) {}.also { hold(it, rep) { down(); t.text = value() } }
        box.addView(u); box.addView(t); box.addView(d)
        return box
    }
    private fun showEditor() {
        stopAudio(); curRec = null; curTts = null; useRec = true; styleIdx = 0; daily = true
        val c = Calendar.getInstance().apply { add(Calendar.MINUTE, 2) }
        eh = if (c.get(Calendar.HOUR) == 0) 12 else c.get(Calendar.HOUR)
        em = c.get(Calendar.MINUTE); pm = c.get(Calendar.AM_PM) == Calendar.PM
        inEditor = true
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(36), dp(20), dp(40)) }
        root.addView(tv("New alarm", 30f, TEAL, true))
        root.addView(tv("1.  Set the time", 20f, INK, true))
        val tc = card()
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(col(true, { eh.toString() }, { eh = eh % 12 + 1 }, { eh = if (eh == 1) 12 else eh - 1 }))
        row.addView(tv(":", 48f, INK, true))
        row.addView(col(true, { String.format("%02d", em) }, { em = (em + 1) % 60 }, { em = (em + 59) % 60 }))
        row.addView(col(false, { if (pm) "PM" else "AM" }, { pm = !pm }, { pm = !pm }))
        tc.addView(row); root.addView(tc)
        root.addView(tv("2.  Name (optional)", 20f, INK, true).apply { setPadding(0, dp(18), 0, dp(4)) })
        val nameEt = EditText(this).apply {
            hint = "For example: Go to gym"; textSize = 20f; setTextColor(INK); setHintTextColor(MUTE); setSingleLine(true)
        }
        root.addView(nameEt)
        root.addView(tv("3.  Your wake-up voice", 20f, INK, true).apply { setPadding(0, dp(18), 0, dp(4)) })
        root.addView(buildVoiceCard())
        lateinit var rBtn: Button
        rBtn = btn("Repeat: every day", CARD2, INK) { daily = !daily; rBtn.text = if (daily) "Repeat: every day" else "Repeat: only once" }
        root.addView(tv("4.  Repeat", 20f, INK, true).apply { setPadding(0, dp(18), 0, dp(4)) })
        root.addView(rBtn)
        root.addView(btn("Save alarm") { save(nameEt.text.toString().trim()) }.apply {
            (layoutParams as LinearLayout.LayoutParams).topMargin = dp(28) })
        root.addView(btn("Cancel", CARD2, INK) { leaveEditor() })
        page(root)
    }
    private fun buildVoiceCard(): LinearLayout {
        val vc = card()
        val tabs = LinearLayout(this)
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        status = tv("", 17f, MUTE)
        lateinit var refresh: () -> Unit
        lateinit var sBtn: Button
        val bRec = btn("My own voice") { useRec = true; refresh() }
        val bTts = btn("Typed message") { useRec = false; refresh() }
        listOf(bRec, bTts).forEach {
            it.layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { topMargin = dp(4); marginEnd = dp(4) }
            it.textSize = 17f; tabs.addView(it)
        }
        sBtn = btn("Voice style: ${names[0]}  (tap to change)", CARD2, INK) {
            styleIdx = (styleIdx + 1) % names.size; sBtn.text = "Voice style: ${names[styleIdx]}  (tap to change)"
        }
        refresh = {
            if (rec != null) stopRec()
            (bRec.background as GradientDrawable).setColor(if (useRec) TEAL else CARD2); bRec.setTextColor(if (useRec) DARK else INK)
            (bTts.background as GradientDrawable).setColor(if (useRec) CARD2 else TEAL); bTts.setTextColor(if (useRec) INK else DARK)
            panel.removeAllViews(); status.text = ""
            if (useRec) {
                panel.addView(tv("Tap Start, say why you need to wake up, then tap Stop. About 15 seconds or more works best.", 17f, MUTE))
                recBtn = btn("Start recording") { toggleRec() }
                panel.addView(recBtn)
            } else {
                recBtn = null
                val et = EditText(this).apply {
                    setText("Wake up! It is time to get up."); textSize = 20f; setTextColor(INK); minLines = 2
                }
                panel.addView(tv("Type what the voice should say:", 17f, MUTE)); panel.addView(et)
                panel.addView(btn("Create voice from message") { makeVoice(et.text.toString()) })
            }
            panel.addView(status)
            panel.addView(btn("Play it back", CARD2, INK) { preview() })
        }
        vc.addView(tabs); vc.addView(panel); vc.addView(sBtn)
        refresh()
        return vc
    }
    private fun leaveEditor() { if (rec != null) stopRec(); stopAudio(); showHome() }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (inEditor) leaveEditor() else super.onBackPressed() }

    // ---------- recording ----------
    private val tick = object : Runnable {
        override fun run() {
            if (rec == null) return
            val s = ((System.currentTimeMillis() - recStart) / 1000).toInt()
            if (s >= 120) { stopRec(); return }
            status.text = "Recording…  ${s / 60}:${String.format("%02d", s % 60)}"
            h.postDelayed(this, 500)
        }
    }
    private fun setRecUi(on: Boolean) {
        recBtn?.let {
            it.text = if (on) "Stop recording" else "Record again"
            (it.background as GradientDrawable).setColor(if (on) RED else TEAL)
            it.setTextColor(if (on) -1 else DARK)
        }
    }
    @Suppress("DEPRECATION")
    private fun toggleRec() {
        if (rec != null) { stopRec(); return }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1); return
        }
        stopAudio()
        val f = File(filesDir, "tmp_rec.m4a")
        try {
            rec = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC); setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC); setOutputFile(f.path); prepare(); start()
            }
            recStart = System.currentTimeMillis(); setRecUi(true); h.post(tick)
        } catch (e: Exception) { rec = null; status.text = "Could not start recording. Please try again." }
    }
    private fun stopRec() {
        h.removeCallbacks(tick)
        val secs = ((System.currentTimeMillis() - recStart) / 1000).toInt()
        var ok = true
        try { rec?.stop() } catch (e: Exception) { ok = false }
        rec?.release(); rec = null; setRecUi(false)
        if (!ok) { curRec = null; status.text = "That was too short. Please try again."; return }
        curRec = File(filesDir, "tmp_rec.m4a")
        status.text = if (secs < 15) "Saved ($secs sec). Tip: 15 seconds or more works best. You can record again."
        else "Saved ($secs sec). Tap \"Play it back\" to listen."
    }

    // ---------- AI voice ----------
    private fun makeVoice(t: String) {
        if (t.isBlank()) { status.text = "Please type a message first."; return }
        if (!ttsOk) { status.text = "Text-to-speech is not ready. Check your phone's text-to-speech settings."; return }
        val f = File(filesDir, "tmp_tts.wav"); f.delete(); status.text = "Creating voice…"
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(u: String?) {}
            override fun onDone(u: String?) { runOnUiThread { curTts = f; status.text = "Voice ready. Tap \"Play it back\"." } }
            override fun onError(u: String?) { runOnUiThread { status.text = "Could not create the voice." } }
        })
        if (tts?.synthesizeToFile(t, Bundle(), f, "u") != TextToSpeech.SUCCESS) status.text = "Could not create the voice."
    }

    private fun stopAudio() { mp?.release(); mp = null }
    private fun preview() {
        val f = cur
        if (f == null || !f.exists()) { status.text = if (useRec) "Record your voice first." else "Create the voice first."; return }
        stopAudio()
        mp = MediaPlayer().apply { setDataSource(f.path); prepare(); start() }
        try { mp?.playbackParams = PlaybackParams().setPitch(pitches[styleIdx]).setSpeed(1f) } catch (e: Exception) {}
    }

    private fun save(name: String) {
        val src = cur
        if (src == null || !src.exists()) {
            status.text = if (useRec) "Please record your voice first." else "Please tap \"Create voice from message\" first."; return
        }
        val id = (System.currentTimeMillis() / 1000).toInt()
        val dest = File(filesDir, "a$id.${src.extension}"); src.copyTo(dest, true)
        val h24 = (eh % 12) + if (pm) 12 else 0
        val a = Alarm(id, h24, em, name.ifBlank { "Wake up" }, dest.path, pitches[styleIdx], daily, true)
        val l = Store.all(this); l.add(a); Store.save(this, l); Sched.set(this, a)
        val mins = (Sched.next(a) - System.currentTimeMillis()) / 60000
        toast("Alarm set for ${fmt(h24, em)}\nRings in " + (if (mins >= 60) "${mins / 60} h ${mins % 60} min" else "$mins min"))
        stopAudio(); showHome()
    }

    override fun onDestroy() { tts?.shutdown(); stopAudio(); super.onDestroy() }
}
