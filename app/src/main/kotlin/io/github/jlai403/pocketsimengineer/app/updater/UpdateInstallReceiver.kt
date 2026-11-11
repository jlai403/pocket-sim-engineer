package io.github.jlai403.pocketsimengineer.app.updater

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build

class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != UpdateManager.ACTION_INSTALL_STATUS) return
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = intent.parcelableExtra(Intent.EXTRA_INTENT)
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(confirm) }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> UpdateManager.onInstallResult(true, null)
            else -> UpdateManager.onInstallResult(
                false,
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "status $status",
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun Intent.parcelableExtra(name: String): Intent? =
        if (Build.VERSION.SDK_INT >= 33) {
            getParcelableExtra(name, Intent::class.java)
        } else {
            getParcelableExtra(name)
        }
}
