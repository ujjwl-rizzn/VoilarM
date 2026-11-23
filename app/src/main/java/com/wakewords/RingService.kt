package com.wakewords
import android.app.*
import android.content.Context
import android.content.Intent
import android.media.*
import android.media.audiofx.LoudnessEnhancer
import android.os.*

class RingService : Service() {
    companion object { @Volatile var running = false; @Volatile var pct = 20 }
    private var mp: MediaPlayer? = null
    private var enh: LoudnessEnhancer? = null
    private var wl: PowerManager.WakeLock? = null
    private val h = Handler(Looper.getMainLooper())
    private val levels = floatArrayOf(.2f, .3f, .4f, .5f, 1f, 1.2f, 1.5f)
    private var lvl = 0
    private var alarmId = 0
    private val ramp = object : Runnable {
        override fun run() { if (lvl < levels.size - 1) { lvl++; applyLevel(); h.postDelayed(this, 10000) } }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        when (i?.action) {
            "stop" -> { stopAll(); return START_NOT_STICKY }
            "snooze" -> { Sched.snooze(this, alarmId); stopAll(); return START_NOT_STICKY }
        }
        running = true
        alarmId = i?.getIntExtra("id", 0) ?: 0
        val a = Store.get(this, alarmId)
        if (a == null) { stopAll(); return START_NOT_STICKY }
        startForeground(1, notification(a))
        wl = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ww:ring").apply { acquire(11 * 60 * 1000L) }
        play(a)
        h.postDelayed({ stopAll() }, 600000)
        return START_NOT_STICKY
    }

    private fun notification(a: Alarm): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("ring", "Alarm", NotificationManager.IMPORTANCE_HIGH).apply { setSound(null, null) })
        val fl = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val full = PendingIntent.getActivity(this, 1, Intent(this, RingActivity::class.java).putExtra("id", a.id), fl)
        fun act(s: String, code: Int) = PendingIntent.getService(this, code, Intent(this, RingService::class.java).setAction(s), fl)
        return Notification.Builder(this, "ring").setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(a.label).setContentText("Alarm is ringing").setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true).setContentIntent(full).setFullScreenIntent(full, true)
            .addAction(0, "Stop", act("stop", 2)).addAction(0, "Snooze 5 min", act("snooze", 3)).build()
    }

    private fun play(a: Alarm) {
        val am = getSystemService(AudioManager::class.java)
        am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        mp = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            setDataSource(a.file); isLooping = true; prepare()
        }
        try { enh = LoudnessEnhancer(mp!!.audioSessionId).also { it.enabled = true } } catch (e: Exception) {}
        lvl = 0; applyLevel()
        mp?.start()
        try { mp?.playbackParams = PlaybackParams().setPitch(a.pitch).setSpeed(1f) } catch (e: Exception) {}
        h.postDelayed(ramp, 10000)
        getSystemService(Vibrator::class.java).vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 500), 0))
    }

    private fun applyLevel() {
        val l = levels[lvl]; pct = (l * 100).toInt()
        mp?.setVolume(minOf(l, 1f), minOf(l, 1f))
        try { enh?.setTargetGain(if (l > 1f) (2000 * Math.log10(l.toDouble())).toInt() else 0) } catch (e: Exception) {}
    }

    private fun stopAll() {
        running = false; h.removeCallbacksAndMessages(null)
        try { mp?.stop() } catch (e: Exception) {}
        mp?.release(); mp = null; enh?.release(); enh = null
        if (wl?.isHeld == true) wl?.release()
        getSystemService(Vibrator::class.java).cancel()
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }

    override fun onDestroy() { stopAll(); super.onDestroy() }
}
