package dev.rezy.veil

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dev.rezy.veil.data.Settings

/** Restarts the long poll after a reboot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val settings = Settings(context)
        if (!settings.isConfigured || !settings.watches) return

        runCatching {
            ContextCompat.startForegroundService(
                context, Intent(context, WatchService::class.java)
            )
        }
    }
}
