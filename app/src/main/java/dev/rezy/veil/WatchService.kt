package dev.rezy.veil

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import dev.rezy.veil.data.Settings
import dev.rezy.veil.net.VeilClient
import org.json.JSONObject

/**
 * Holds a long poll against the daemon's /watch endpoint so the phone learns
 * about a lock or a password prompt within a second, without cloud push.
 *
 * Runs as a foreground service: a plain background thread would be frozen.
 */
class WatchService : Service() {

    private var running = true
    private var worker: Thread? = null
    private var lastPrompt = false
    private var lastLocked = false

    override fun onCreate() {
        super.onCreate()
        createChannels()
        startForegroundCompat()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (worker == null) {
            worker = Thread { loop() }.apply {
                isDaemon = true
                start()
            }
        }
        return START_STICKY
    }

    private fun loop() {
        val settings = Settings(this)
        var since = ""

        while (running) {
            if (!settings.isConfigured) {
                Thread.sleep(5000)
                continue
            }

            val path = if (since.isEmpty()) "/watch" else "/watch?since=$since"
            val response = runCatching {
                VeilClient.call(
                    settings.host, settings.port, settings.secret,
                    "GET", path,
                    secure = settings.secure,
                    readTimeoutMs = 40000
                )
            }.getOrNull()

            if (response == null || !response.ok) {
                Thread.sleep(4000)
                continue
            }

            val json = runCatching { JSONObject(response.body) }.getOrNull() ?: continue
            since = json.optString("sig", "")

            val prompting = json.optBoolean("prompting", false)
            val locked = json.optBoolean("locked", false)
            val focus = json.optJSONObject("focus")?.optString("class").orEmpty()

            when {
                prompting && !lastPrompt -> post(
                    "Password requested",
                    if (focus.isNotBlank()) "Tap to send it to $focus" else "Tap to send your password",
                    ACTION_SEND
                )

                locked && !lastLocked -> post(
                    "Desktop locked",
                    "Tap to unlock with your fingerprint",
                    ACTION_UNLOCK
                )

                !prompting && !locked && (lastPrompt || lastLocked) -> cancelPrompt()
            }

            lastPrompt = prompting
            lastLocked = locked
        }
    }

    private fun post(title: String, text: String, action: String) {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_ACTION, action)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = Notification.Builder(this, PROMPT_CHANNEL)
            .setSmallIcon(R.drawable.ic_fingerprint)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .build()

        getSystemService(NotificationManager::class.java).notify(PROMPT_ID, notification)
    }

    private fun cancelPrompt() {
        getSystemService(NotificationManager::class.java).cancel(PROMPT_ID)
    }

    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        manager.createNotificationChannel(
            NotificationChannel(
                SERVICE_CHANNEL,
                "Veil watcher",
                NotificationManager.IMPORTANCE_MIN
            ).apply { setShowBadge(false) }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                PROMPT_CHANNEL,
                "Password and lock prompts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Tells you when the desktop is locked or asking for a password"
                enableVibration(true)
            }
        )
    }

    private fun startForegroundCompat() {
        val notification = Notification.Builder(this, SERVICE_CHANNEL)
            .setSmallIcon(R.drawable.ic_fingerprint)
            .setContentTitle("Veil watching")
            .setContentText("Listening for lock and password prompts")
            .setOngoing(true)
            .setShowWhen(false)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                SERVICE_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(SERVICE_ID, notification)
        }
    }

    override fun onDestroy() {
        running = false
        worker?.interrupt()
        worker = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_SEND = "send"
        const val ACTION_UNLOCK = "unlock"

        private const val SERVICE_CHANNEL = "veil_watch_service"
        private const val PROMPT_CHANNEL = "veil_prompts"
        private const val SERVICE_ID = 2001
        private const val PROMPT_ID = 2002
    }
}
