package com.example.limit

import android.app.Application

class LimitApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}
