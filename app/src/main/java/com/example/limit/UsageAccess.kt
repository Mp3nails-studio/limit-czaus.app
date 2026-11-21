package com.example.limit

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process

/** Sprawdzanie, która aplikacja jest na pierwszym planie (wymaga "Dostępu do danych o użyciu"). */
object UsageAccess {

    @Suppress("DEPRECATION")
    fun granted(c: Context): Boolean {
        val ops = c.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName
            )
        } else {
            ops.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Pakiet aplikacji, która ostatnio weszła na pierwszy plan w ostatnich [windowMs] ms. */
    @Suppress("DEPRECATION")
    fun latestForeground(c: Context, windowMs: Long): String? {
        val usm = c.getSystemService(UsageStatsManager::class.java) ?: return null
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - windowMs, now)
        val e = UsageEvents.Event()
        var pkg: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) pkg = e.packageName
        }
        return pkg
    }

    private val allowedPackages = setOf(
        // okno odinstalowania i uprawnień otwierane z Ustawień
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.samsung.android.packageinstaller",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller"
    )

    /** Ustawienia systemowe (i okna z nimi związane), przy których blokada ma się schować. */
    fun isSettingsPackage(pkg: String): Boolean =
        pkg.startsWith("com.android.settings") || pkg in allowedPackages
}
