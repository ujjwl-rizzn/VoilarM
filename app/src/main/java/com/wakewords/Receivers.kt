package com.wakewords
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
        c.startForegroundService(Intent(c, RingService::class.java).putExtra("id", id))
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) { Store.all(c).filter { it.on }.forEach { Sched.set(c, it) } }
}
