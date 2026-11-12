package io.github.jlai403.pocketsimengineer.app

import android.app.Application

class PocketSimEngineerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Diagnostics.init(this)
    }
}
