package com.wakewords
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getIntExtra("id", 0)
        val a = Store.get(c, id) ?: return
        if (!i.getBooleanExtra("snooze", false)) {
            if (!a.on) return
            if (a.daily) Sched.set(c, a) else Store.setOn(c, id, false)
        }
        try {
            c.startForegroundService(Intent(c, RingService::class.java).putExtra("id", id))
        } catch (e: Exception) {
            // Fallback: full-screen notification that opens the ringing screen, which starts the sound
            val nm = c.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel("ring", "Alarm", NotificationManager.IMPORTANCE_HIGH))
            val pi = PendingIntent.getActivity(c, 1,
                Intent(c, RingActivity::class.java).putExtra("id", id).putExtra("start", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            nm.notify(9, Notification.Builder(c, "ring").setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(a.label).setContentText("Tap to open your alarm").setCategory(Notification.CATEGORY_ALARM)
                .setFullScreenIntent(pi, true).setContentIntent(pi).setAutoCancel(true).build())
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) { Store.all(c).filter { it.on }.forEach { Sched.set(c, it) } }
}
