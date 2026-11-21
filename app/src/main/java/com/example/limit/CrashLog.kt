package com.example.limit

import android.app.Application
import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/** Zapisuje błędy do pliku, żeby po ponownym otwarciu aplikacji dało się je zobaczyć i skopiować. */
object CrashLog {
    private const val FILE = "crash.txt"

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                write(app, "Wątek: ${thread.name}", error)
                // Po crashu zresetuj stan limitu, żeby usługa nie wpadła w pętlę restartów.
                app.getSharedPreferences("limit", Context.MODE_PRIVATE)
                    .edit().remove("end").remove("locked").remove("expiry").remove("paused_at").commit()
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Błąd obsłużony (aplikacja się nie wysypała), ale warto go zobaczyć. */
    fun record(c: Context, error: Throwable) {
        try {
            write(c, "Błąd obsłużony (bez wysypania aplikacji)", error)
        } catch (_: Throwable) {
        }
    }

    private fun write(c: Context, header: String, error: Throwable) {
        val sw = StringWriter()
        error.printStackTrace(PrintWriter(sw))
        val text = buildString {
            appendLine(header)
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine()
            append(sw.toString())
        }.take(12_000)
        File(c.filesDir, FILE).writeText(text)
    }

    fun read(c: Context): String? =
        File(c.filesDir, FILE).takeIf { it.exists() }?.readText()

    fun clear(c: Context) {
        File(c.filesDir, FILE).delete()
    }
}
