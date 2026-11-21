package com.example.limit

import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.DynamicColors
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class LimitService : Service() {

    companion object {
        private const val ACTION_CANCEL = "com.example.limit.CANCEL"
        private const val ACTION_TIME_UP = "com.example.limit.TIME_UP"
        private const val ACTION_MIDNIGHT = "com.example.limit.MIDNIGHT"
        private const val CHANNEL_ID = "limit_timer"
        private const val NOTIF_ID = 1
        private const val ALARM_DURATION_MS = 30_000L

        /** Zapisuje koniec limitu (i najbliższą północ, po której limit sam się wyłącza) i uruchamia usługę. */
        fun start(ctx: Context, durationMs: Long) {
            val now = System.currentTimeMillis()
            Prefs.setEndTime(ctx, now + durationMs)
            Prefs.setLocked(ctx, false)
            Prefs.setPausedAt(ctx, 0L)
            Prefs.setExpiry(ctx, Prefs.nextMidnight(now))
            ContextCompat.startForegroundService(ctx, Intent(ctx, LimitService::class.java))
        }

        /** Wznawia usługę na podstawie zapisanego stanu (np. po restarcie). */
        fun resume(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, LimitService::class.java))
        }

        /** Anulowanie limitu (hasło sprawdza UI). */
        fun cancel(ctx: Context) {
            Prefs.clear(ctx)
            ctx.startService(Intent(ctx, LimitService::class.java).setAction(ACTION_CANCEL))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var overlay: View? = null
    private var player: MediaPlayer? = null

    // Zapas gdyby alarm systemowy się spóźnił - sprawdza czas co sekundę.
    private val ticker = object : Runnable {
        override fun run() {
            if (expireIfNeeded()) return
            step { evaluatePause() }
            // Gdy telefon jest zablokowany, odliczanie stoi - nie sprawdzamy końca limitu.
            if (Prefs.pausedAt(this@LimitService) > 0L) {
                handler.postDelayed(this, 1000)
                return
            }
            val end = Prefs.endTime(this@LimitService)
            if (end > 0L && System.currentTimeMillis() >= end) {
                step { onTimeUp() }
            } else {
                handler.postDelayed(this, 1000)
            }
        }
    }

    private var lastForeground: String? = null

    /**
     * Gdy telefon jest zablokowany: jeśli na pierwszym planie są Ustawienia, chowa nakładkę
     * (żeby dało się wejść i odinstalować). Gdy tylko użytkownik z nich wyjdzie, nakładka wraca.
     */
    private val watcher = object : Runnable {
        override fun run() {
            if (expireIfNeeded()) return
            if (!Prefs.locked(this@LimitService)) return
            var inSettings = false
            try {
                if (UsageAccess.granted(this@LimitService)) {
                    val window = if (lastForeground == null) 3_600_000L else 10_000L
                    UsageAccess.latestForeground(this@LimitService, window)?.let { lastForeground = it }
                    inSettings = lastForeground?.let { UsageAccess.isSettingsPackage(it) } == true
                }
            } catch (e: Throwable) {
                // Bez wykrywania Ustawień blokada i tak działa - tylko nie da się z niej wyjść do Ustawień.
                CrashLog.record(this@LimitService, e)
            }
            try {
                if (inSettings) removeOverlay() else showOverlay()
            } catch (e: Throwable) {
                CrashLog.record(this@LimitService, e)
            }
            handler.postDelayed(this, 500)
        }
    }

    private fun startWatcher() {
        handler.removeCallbacks(watcher)
        handler.post(watcher)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        try {
            handleStart(intent)
        } catch (e: Exception) {
            CrashLog.record(this, e)
            Prefs.clear(this)
            stopSelf()
            START_NOT_STICKY
        }

    private fun handleStart(intent: Intent?): Int {
        createChannel()

        val end = Prefs.endTime(this)
        val locked = Prefs.locked(this)

        // Po startForegroundService trzeba w ciągu 5 s wywołać startForeground.
        val pausedAt = Prefs.pausedAt(this)
        val first = when {
            locked -> lockedNotification()
            pausedAt > 0L -> pausedNotification(end - pausedAt)
            else -> countdownNotification(end)
        }
        ServiceCompat.startForeground(
            this, NOTIF_ID, first,
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )

        if (intent?.action == ACTION_CANCEL || (end == 0L && !locked)) {
            shutdown()
            return START_NOT_STICKY
        }

        // Po północy (np. po restarcie telefonu albo z alarmu o północy) limit i blokada znikają.
        if (expireIfNeeded()) return START_NOT_STICKY

        step { scheduleMidnightAlarm(Prefs.expiry(this)) }

        if (locked) {
            // Wznowienie po restarcie / ubiciu procesu: tylko pokaż blokadę, bez dzwonka.
            startWatcher()
        } else {
            step { registerScreenReceiver() }
            step { evaluatePause() }
            val endNow = Prefs.endTime(this)
            if (Prefs.pausedAt(this) > 0L) {
                // Telefon zablokowany: odliczanie stoi, ticker tylko pilnuje zmian i północy.
                handler.removeCallbacks(ticker)
                handler.postDelayed(ticker, 1000)
            } else if (System.currentTimeMillis() >= endNow) {
                onTimeUp()
            } else {
                scheduleAlarm(endNow)
                handler.removeCallbacks(ticker)
                handler.postDelayed(ticker, 1000)
            }
        }
        return START_STICKY
    }

    // ---------- pauza, gdy telefon jest zablokowany ----------

    private var screenReceiver: BroadcastReceiver? = null

    private fun registerScreenReceiver() {
        if (screenReceiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                step { evaluatePause() }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, r, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        screenReceiver = r
    }

    private fun unregisterScreenReceiver() {
        screenReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {
            }
        }
        screenReceiver = null
    }

    /** Telefon uznajemy za zablokowany, gdy ekran jest wygaszony albo widać ekran blokady. */
    private fun phoneIsLocked(): Boolean {
        val pm = getSystemService(PowerManager::class.java)
        val km = getSystemService(KeyguardManager::class.java)
        return !pm.isInteractive || km.isKeyguardLocked
    }

    private fun formatRemaining(ms: Long): String {
        val s = (ms.coerceAtLeast(0) + 999) / 1000
        return "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
    }

    /**
     * Zatrzymuje odliczanie, gdy telefon się blokuje, i wznawia po odblokowaniu
     * (przesuwając koniec limitu o czas przerwy).
     */
    private fun evaluatePause() {
        if (Prefs.locked(this)) return
        val end = Prefs.endTime(this)
        if (end <= 0L) return
        val now = System.currentTimeMillis()
        val pausedAt = Prefs.pausedAt(this)
        val shouldPause = phoneIsLocked()
        val nm = getSystemService(NotificationManager::class.java)

        if (shouldPause && pausedAt == 0L) {
            if (now >= end) return // czas już minął - niech zadziała normalny koniec limitu
            Prefs.setPausedAt(this, now)
            step { cancelAlarm() }
            step { nm.notify(NOTIF_ID, pausedNotification(end - now)) }
        } else if (!shouldPause && pausedAt > 0L) {
            val newEnd = end + (now - pausedAt)
            Prefs.setEndTime(this, newEnd)
            Prefs.setPausedAt(this, 0L)
            step { scheduleAlarm(newEnd) }
            step { nm.notify(NOTIF_ID, countdownNotification(newEnd)) }
            handler.removeCallbacks(ticker)
            handler.postDelayed(ticker, 1000)
        }
    }

    // ---------- północ: limit i blokada same się wyłączają ----------

    /** Jeśli minęła zapisana północ, czyści limit i blokadę, zatrzymuje usługę i zwraca true. */
    private fun expireIfNeeded(): Boolean {
        val expiry = Prefs.expiry(this)
        if (expiry <= 0L || System.currentTimeMillis() < expiry) return false
        Prefs.clear(this)
        shutdown()
        return true
    }

    private fun midnightIntent(): PendingIntent = PendingIntent.getForegroundService(
        this, 3,
        Intent(this, LimitService::class.java).setAction(ACTION_MIDNIGHT),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** Alarm budzący usługę o północy, nawet gdy telefon śpi (system może spóźnić go o kilka minut). */
    private fun scheduleMidnightAlarm(expiry: Long) {
        if (expiry <= 0L) return
        getSystemService(AlarmManager::class.java)
            .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, expiry, midnightIntent())
    }

    private fun cancelMidnightAlarm() {
        getSystemService(AlarmManager::class.java).cancel(midnightIntent())
    }

    // ---------- koniec limitu ----------

    private fun onTimeUp() {
        handler.removeCallbacks(ticker)
        val wasLocked = Prefs.locked(this)
        Prefs.setLocked(this, true)
        Prefs.setPausedAt(this, 0L)
        step { unregisterScreenReceiver() }
        lastForeground = null

        // Każdy krok osobno, żeby błąd w jednym nie wyłączył reszty (blokady, dzwonka).
        step { cancelAlarm() }
        step { getSystemService(NotificationManager::class.java).notify(NOTIF_ID, lockedNotification()) }
        step { startWatcher() }
        if (!wasLocked) step { playAlarm() }
    }

    private inline fun step(block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            CrashLog.record(this, e)
        }
    }

    private fun unlock() {
        stopAlarm()
        Prefs.clear(this)
        shutdown()
    }

    private fun shutdown() {
        handler.removeCallbacksAndMessages(null)
        step { unregisterScreenReceiver() }
        step { cancelAlarm() }
        step { cancelMidnightAlarm() }
        stopAlarm()
        removeOverlay()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ---------- alarm systemowy (działa też przy uśpionym telefonie) ----------

    private fun alarmIntent(): PendingIntent = PendingIntent.getForegroundService(
        this, 1,
        Intent(this, LimitService::class.java).setAction(ACTION_TIME_UP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun scheduleAlarm(end: Long) {
        val show = PendingIntent.getActivity(
            this, 2, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        try {
            getSystemService(AlarmManager::class.java)
                .setAlarmClock(AlarmManager.AlarmClockInfo(end, show), alarmIntent())
        } catch (e: Exception) {
            // Bez alarmu systemowego działa zapasowe sprawdzanie co sekundę (ticker).
            CrashLog.record(this, e)
        }
    }

    private fun cancelAlarm() {
        getSystemService(AlarmManager::class.java).cancel(alarmIntent())
    }

    // ---------- dzwonek 30 s ----------

    private fun playAlarm() {
        stopAlarm()
        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val mp = MediaPlayer.create(this, R.raw.alarm, attrs, AudioManager.AUDIO_SESSION_ID_GENERATE)
                ?: return
            mp.isLooping = true
            mp.start()
            player = mp
            handler.postDelayed({ stopAlarm() }, ALARM_DURATION_MS)
        } catch (_: Exception) {
        }
    }

    private fun stopAlarm() {
        player?.let {
            try {
                if (it.isPlaying) it.stop()
            } catch (_: Exception) {
            }
            it.release()
        }
        player = null
    }

    // ---------- ekran "Czas minął!" nad wszystkim ----------

    private var overlayFailed = false

    private fun showOverlay() {
        if (overlayFailed) return
        try {
            showOverlayUnsafe()
        } catch (e: Throwable) {
            overlayFailed = true
            CrashLog.record(this, e)
        }
    }

    private fun showOverlayUnsafe() {
        if (overlay != null) return
        if (!Settings.canDrawOverlays(this)) return

        // Kolory: dynamiczne (z tapety) albo wybrany kolor przewodni - tak samo jak w aplikacji.
        val choice = Prefs.colorChoice(this)
        val base = ContextThemeWrapper(this, R.style.Theme_LimitCzasu)
        val dynamic = AppColors.useDynamic(choice)
        val themed = if (dynamic) DynamicColors.wrapContextIfAvailable(base) else base
        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        val pal = if (dynamic) AppColors.palFromTheme(themed)
        else AppColors.palFromSeed(AppColors.hueFor(choice), dark)

        val view = LayoutInflater.from(themed).inflate(R.layout.overlay_lock, null)

        val layout = view.findViewById<TextInputLayout>(R.id.pin_layout)
        val input = view.findViewById<TextInputEditText>(R.id.pin_input)
        val panel = view.findViewById<View>(R.id.pin_panel)
        val slider = view.findViewById<SlideToUnlockView>(R.id.slider)
        val unlockButton = view.findViewById<MaterialButton>(R.id.unlock_button)
        val backButton = view.findViewById<MaterialButton>(R.id.back_button)
        val imm = getSystemService(InputMethodManager::class.java)

        // Kolory wybrane w ustawieniach aplikacji.
        view.setBackgroundColor(pal.surface)
        view.findViewById<TextView>(R.id.lock_title).setTextColor(pal.error)
        input.setTextColor(pal.onSurface)
        layout.setBoxStrokeColor(pal.primary)
        layout.defaultHintTextColor = ColorStateList.valueOf(pal.onSurfaceVariant)
        layout.hintTextColor = ColorStateList.valueOf(pal.primary)
        layout.setErrorTextColor(ColorStateList.valueOf(pal.error))
        layout.setEndIconTintList(ColorStateList.valueOf(pal.onSurfaceVariant))
        unlockButton.backgroundTintList = ColorStateList.valueOf(pal.primary)
        unlockButton.setTextColor(pal.onPrimary)
        backButton.setTextColor(pal.primary)
        slider.label = getString(R.string.lock_slide)
        slider.setColors(pal.surfaceVariant, pal.primary, pal.onPrimary, pal.onSurfaceVariant)

        fun showSlider() {
            imm.hideSoftInputFromWindow(input.windowToken, 0)
            input.text?.clear()
            layout.error = null
            panel.visibility = View.GONE
            slider.visibility = View.VISIBLE
        }

        fun showPanel() {
            slider.visibility = View.GONE
            panel.visibility = View.VISIBLE
            input.requestFocus()
            // Klawiatura pokazuje się dopiero po przeciągnięciu suwaka.
            input.postDelayed({
                input.requestFocus()
                imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }, 150)
        }

        fun tryUnlock() {
            if (input.text?.toString() == Prefs.password(this)) {
                imm.hideSoftInputFromWindow(input.windowToken, 0)
                unlock()
            } else {
                layout.error = getString(R.string.lock_wrong)
                input.text?.clear()
            }
        }

        slider.onSlid = { showPanel() }
        backButton.setOnClickListener { showSlider() }
        unlockButton.setOnClickListener { tryUnlock() }
        input.setOnEditorActionListener { _, _, _ -> tryUnlock(); true }

        @Suppress("DEPRECATION")
        view.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.OPAQUE
        )
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        getSystemService(WindowManager::class.java).addView(view, lp)
        overlay = view
    }

    private fun removeOverlay() {
        overlay?.let {
            try {
                getSystemService(WindowManager::class.java).removeView(it)
            } catch (_: Exception) {
            }
        }
        overlay = null
    }

    // ---------- powiadomienia ----------

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, getString(R.string.notif_channel),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
    )

    /** Powiadomienie z odliczaniem - system sam odświeża stoper, bez budzenia aplikacji. */
    private fun countdownNotification(end: Long): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setWhen(end)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppIntent())
            .build()

    /** Powiadomienie na czas pauzy: bez żywego odliczania, pokazuje ile zostało. */
    private fun pausedNotification(remainingMs: Long): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(getString(R.string.notif_paused_title))
            .setContentText(getString(R.string.notif_paused_text, formatRemaining(remainingMs)))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppIntent())
            .build()

    private fun lockedNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(getString(R.string.notif_locked_title))
            .setContentText(getString(R.string.notif_locked_text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent())
            .build()

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopAlarm()
        removeOverlay()
        super.onDestroy()
    }
}
