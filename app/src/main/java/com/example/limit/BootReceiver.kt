package com.example.limit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Po restarcie telefonu wznawia limit (lub blokadę), jeśli były aktywne. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED &&
            (Prefs.endTime(context) > 0L || Prefs.locked(context))
        ) {
            LimitService.resume(context)
        }
    }
}
