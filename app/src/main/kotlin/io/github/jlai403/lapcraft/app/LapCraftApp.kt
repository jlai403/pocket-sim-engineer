package io.github.jlai403.lapcraft.app

import android.app.Application

class LapCraftApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Diagnostics.init(this)
    }
}
