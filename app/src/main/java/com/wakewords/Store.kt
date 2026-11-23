package com.wakewords
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class Alarm(val id: Int, val hour: Int, val minute: Int, val label: String, val file: String,
                 val pitch: Float, val daily: Boolean, var on: Boolean)

object Store {
    private fun p(c: Context) = c.getSharedPreferences("w", Context.MODE_PRIVATE)
    fun all(c: Context): MutableList<Alarm> {
        val out = mutableListOf<Alarm>()
        val arr = JSONArray(p(c).getString("a", "[]"))
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(Alarm(o.getInt("id"), o.getInt("h"), o.getInt("m"), o.getString("l"), o.getString("f"),
                o.getDouble("p").toFloat(), o.getBoolean("d"), o.getBoolean("o")))
        }
        return out
    }
    fun save(c: Context, l: List<Alarm>) {
        val arr = JSONArray()
        l.forEach { arr.put(JSONObject().put("id", it.id).put("h", it.hour).put("m", it.minute).put("l", it.label)
            .put("f", it.file).put("p", it.pitch.toDouble()).put("d", it.daily).put("o", it.on)) }
        p(c).edit().putString("a", arr.toString()).apply()
    }
    fun get(c: Context, id: Int) = all(c).firstOrNull { it.id == id }
    fun setOn(c: Context, id: Int, on: Boolean) { val l = all(c); l.find { it.id == id }?.on = on; save(c, l) }
}

object Sched {
    fun next(a: Alarm): Long {
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, a.hour); set(Calendar.MINUTE, a.minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        return c.timeInMillis
    }
    private fun pi(c: Context, id: Int, snooze: Boolean) = PendingIntent.getBroadcast(
        c, if (snooze) id + 1000000 else id,
        Intent(c, AlarmReceiver::class.java).putExtra("id", id).putExtra("snooze", snooze),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    private fun fire(c: Context, at: Long, p: PendingIntent) {
        val show = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        c.getSystemService(AlarmManager::class.java).setAlarmClock(AlarmManager.AlarmClockInfo(at, show), p)
    }
    fun set(c: Context, a: Alarm) = fire(c, next(a), pi(c, a.id, false))
    fun snooze(c: Context, id: Int, ms: Long = 300000) = fire(c, System.currentTimeMillis() + ms, pi(c, id, true))
    fun cancel(c: Context, id: Int) = c.getSystemService(AlarmManager::class.java).cancel(pi(c, id, false))
}
