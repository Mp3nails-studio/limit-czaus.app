package com.example.limit

import android.content.Context
import android.os.Build
import java.util.Calendar

/** Stan limitu i ustawienia trzymane na dysku, żeby przetrwały ubicie procesu i restart telefonu. */
object Prefs {
    private const val DEFAULT_PASSWORD = "1987"

    private fun sp(c: Context) = c.getSharedPreferences("limit", Context.MODE_PRIVATE)

    // ---------- stan limitu ----------

    fun endTime(c: Context): Long = sp(c).getLong("end", 0L)
    fun setEndTime(c: Context, v: Long) = sp(c).edit().putLong("end", v).apply()

    fun locked(c: Context): Boolean = sp(c).getBoolean("locked", false)
    fun setLocked(c: Context, v: Boolean) = sp(c).edit().putBoolean("locked", v).apply()

    /** Najbliższa północ po starcie limitu - po niej limit i blokada same się wyłączają (0 = brak). */
    fun expiry(c: Context): Long = sp(c).getLong("expiry", 0L)
    fun setExpiry(c: Context, v: Long) = sp(c).edit().putLong("expiry", v).apply()

    /** Od kiedy odliczanie stoi, bo telefon jest zablokowany (0 = nie ma pauzy). */
    fun pausedAt(c: Context): Long = sp(c).getLong("paused_at", 0L)
    fun setPausedAt(c: Context, v: Long) = sp(c).edit().putLong("paused_at", v).apply()

    /** Czyści tylko stan limitu - hasło, kolory i flaga filmiku zostają. */
    fun clear(c: Context) =
        sp(c).edit().remove("end").remove("locked").remove("expiry").remove("paused_at").apply()

    /** Następna północ (00:00 jutro) w lokalnej strefie czasowej. */
    fun nextMidnight(now: Long = System.currentTimeMillis()): Long =
        Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    // ---------- hasło ----------

    fun password(c: Context): String = sp(c).getString("password", DEFAULT_PASSWORD) ?: DEFAULT_PASSWORD
    fun setPassword(c: Context, v: String) = sp(c).edit().putString("password", v).apply()

    // ---------- kolory ----------

    /** "dynamic" (kolory z tapety, Android 12+) albo "seed:N" (numer koloru z AppColors.SEEDS). */
    fun colorChoice(c: Context): String =
        sp(c).getString("color", null)
            ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "dynamic" else "seed:0"

    fun setColorChoice(c: Context, v: String) = sp(c).edit().putString("color", v).apply()

    // ---------- filmik powitalny ----------

    fun welcomeSeen(c: Context): Boolean = sp(c).getBoolean("welcome_seen", false)
    fun setWelcomeSeen(c: Context) = sp(c).edit().putBoolean("welcome_seen", true).apply()
}
