package dev.rezy.veil

import android.Manifest
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import dev.rezy.veil.crypto.Vault
import dev.rezy.veil.data.Settings
import dev.rezy.veil.net.VeilClient
import dev.rezy.veil.ui.AboutScreen
import dev.rezy.veil.ui.RemoteScreen
import dev.rezy.veil.ui.RemoteState
import dev.rezy.veil.ui.SettingsScreen
import dev.rezy.veil.ui.theme.VeilTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.crypto.Cipher

/**
 * Remote control for the Omarchy lock screen.
 *
 * LOCK is a plain request. UNLOCK requires a fingerprint first: the biometric
 * releases the Keystore key that decrypts the saved desktop password, and only
 * then is the password sent to the daemon (over Tailscale, HMAC-signed). The
 * daemon types it into the lock screen so PAM validates it as usual.
 */
class MainActivity : FragmentActivity() {

    private enum class Screen { Remote, Settings, About }

    private lateinit var settings: Settings

    private var screen by mutableStateOf(Screen.Remote)
    private var state by mutableStateOf(RemoteState())

    private var hostField by mutableStateOf("")
    private var portField by mutableStateOf("8765")
    private var secretField by mutableStateOf("")
    private var passwordField by mutableStateOf("")
    private var secureField by mutableStateOf(false)

    private var askedNotifications = false
    private var refreshJob: kotlinx.coroutines.Job? = null

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        settings = Settings(this)
        hostField = settings.host
        portField = settings.port.toString()
        secretField = settings.secret
        secureField = settings.secure

        // First run: nothing is configured, so open on Setup instead of a
        // dead remote screen.
        screen = if (settings.isConfigured) Screen.Remote else Screen.Settings

        setContent {
            VeilTheme {                BackHandler(enabled = screen != Screen.Remote) {
                    screen = Screen.Remote
                }

                when (screen) {
                    Screen.Remote -> RemoteScreen(
                        state = state,
                        endpoint = settings.endpoint,
                        configured = settings.isConfigured,
                        hasPassword = Vault.hasPassword(this@MainActivity),
                        onLock = ::lockDesktop,
                        onUnlock = ::unlockDesktop,
                        onRefresh = { refreshStatus() },
                        onSetup = { screen = Screen.Settings },
                        onAbout = { screen = Screen.About }
                    )

                    Screen.Settings -> SettingsScreen(
                        host = hostField,
                        onHost = { hostField = it },
                        port = portField,
                        onPort = { portField = it },
                        secret = secretField,
                        onSecret = { secretField = it },
                        password = passwordField,
                        onPassword = { passwordField = it },
                        secure = secureField,
                        onSecure = { secureField = it },
                        hasPassword = Vault.hasPassword(this@MainActivity),
                        busy = state.busy,
                        onSave = ::saveSettings,
                        onBack = { screen = Screen.Remote }
                    )

                    Screen.About -> AboutScreen(onBack = { screen = Screen.Remote })
                }
            }
        }

        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        startWatch()
        startPolling()
    }

    override fun onPause() {
        refreshJob?.cancel()
        refreshJob = null
        super.onPause()
    }

    /**
     * Keep the screen honest while it is visible: a prompt can appear on the
     * desktop at any moment, and the notification alone must not be the only
     * thing that knows about it.
     */
    private fun startPolling() {
        refreshJob?.cancel()
        refreshJob = lifecycleScope.launch {
            while (isActive) {
                delay(4000)
                refreshStatus(silent = true)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val action = intent?.getStringExtra(EXTRA_ACTION) ?: return
        intent.removeExtra(EXTRA_ACTION)
        if (!settings.isConfigured) return

        lifecycleScope.launch {
            refreshStatus(silent = true)
            delay(250)
            if (action == WatchService.ACTION_SEND || action == WatchService.ACTION_UNLOCK) {
                unlockDesktop()
            }
        }
    }

    private fun startWatch() {
        if (!settings.isConfigured || !settings.watches) return
        ensureNotificationPermission()
        runCatching {
            ContextCompat.startForegroundService(
                this, Intent(this, WatchService::class.java)
            )
        }
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (askedNotifications) return
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) return

        askedNotifications = true
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    // ------------------------------------------------------------------ //
    // actions
    // ------------------------------------------------------------------ //

    private fun refreshStatus(silent: Boolean = false) {
        if (!settings.isConfigured) {
            state = RemoteState(reachable = false)
            return
        }

        if (!silent) state = state.copy(busy = true, message = null)
        lifecycleScope.launch {
            val response = withContext(Dispatchers.IO) {
                runCatching {
                    VeilClient.call(
                        settings.host, settings.port, settings.secret,
                        "GET", "/status", secure = settings.secure
                    )
                }.getOrNull()
            }

            state = when {
                response == null -> state.copy(
                    busy = false, reachable = false,
                    message = "Cannot reach the desktop"
                )
                !response.ok -> state.copy(
                    busy = false, reachable = true,
                    message = "Rejected: ${response.body}"
                )
                else -> state.copy(
                    busy = false,
                    reachable = true,
                    locked = parseLocked(response.body),
                    prompting = runCatching {
                        JSONObject(response.body).optBoolean("prompting", false)
                    }.getOrDefault(false),
                    focus = focusLabel(response.body),
                    message = null
                )
            }
        }
    }

    private fun lockDesktop() {
        state = state.copy(busy = true, message = null)
        lifecycleScope.launch {
            val response = withContext(Dispatchers.IO) {
                runCatching {
                    VeilClient.call(
                        settings.host, settings.port, settings.secret,
                        "POST", "/lock", secure = settings.secure
                    )
                }.getOrNull()
            }
            state = when {
                response == null -> state.copy(
                    busy = false, reachable = false,
                    message = "Cannot reach the desktop"
                )
                response.ok -> state.copy(
                    busy = false, reachable = true, locked = true, message = null
                )
                else -> state.copy(
                    busy = false, reachable = true,
                    message = "Lock failed"
                )
            }
        }
    }

    private fun unlockDesktop() {
        if (!Vault.hasPassword(this)) {
            setMessage("Set your desktop password first")
            screen = Screen.Settings
            return
        }

        val cipher = try {
            Vault.cipherForDecrypt(this)
        } catch (_: Exception) {
            setMessage("The saved password is no longer usable — enter it again")
            screen = Screen.Settings
            return
        }

        promptBiometric(cipher, "Unlock desktop") {
            val password = try {
                Vault.readPassword(this, cipher)
            } catch (_: Exception) {
                setMessage("Could not decrypt the saved password")
                return@promptBiometric
            }
            sendUnlock(password)
        }
    }

    private fun sendUnlock(password: String) {
        state = state.copy(busy = true, message = null)
        val body = JSONObject().put("password", password).toString()

        lifecycleScope.launch {
            val response = withContext(Dispatchers.IO) {
                runCatching {
                    VeilClient.call(
                        settings.host, settings.port, settings.secret,
                        "POST", "/unlock", body, secure = settings.secure
                    )
                }.getOrNull()
            }
            state = when {
                response == null -> state.copy(
                    busy = false, reachable = false,
                    message = "Cannot reach the desktop"
                )
                response.ok -> state.copy(
                    busy = false, reachable = true, locked = false,
                    prompting = false, message = null
                )
                detail(response.body) == "nothing-is-asking" -> state.copy(
                    busy = false, reachable = true,
                    message = "Nothing on the desktop is asking for a password"
                )
                detail(response.body) == "still-locked" -> state.copy(
                    busy = false, reachable = true,
                    message = "Still locked — check the saved password"
                )
                else -> state.copy(
                    busy = false, reachable = true,
                    message = "Unlock rejected — check the saved password"
                )
            }
        }
    }

    private fun saveSettings() {
        settings.host = hostField.trim()
        settings.port = portField.trim().toIntOrNull() ?: 8765
        settings.secret = secretField.trim()
        settings.secure = secureField

        val password = passwordField
        if (password.isBlank()) {
            screen = Screen.Remote
            startWatch()
            refreshStatus()
            return
        }

        val cipher = try {
            Vault.cipherForEncrypt()
        } catch (e: Exception) {
            setMessage("Keystore unavailable: ${e.message}")
            return
        }

        promptBiometric(cipher, "Save desktop password") {
            try {
                Vault.store(this, cipher, password)
                passwordField = ""
                screen = Screen.Remote
                startWatch()
                refreshStatus()
            } catch (e: Exception) {
                setMessage("Save failed: ${e.message}")
            }
        }
    }

    // ------------------------------------------------------------------ //
    // helpers
    // ------------------------------------------------------------------ //

    private fun promptBiometric(cipher: Cipher, title: String, onSuccess: () -> Unit) {
        if (!authAvailable()) {
            setMessage("Set a screen lock first — PIN, pattern, password or biometric")
            return
        }

        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    onSuccess()
                }

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence
                ) {
                    setMessage(errString.toString())
                }

                override fun onAuthenticationFailed() {
                    // keep the prompt open for another attempt
                }
            }
        )

        val info = BiometricPrompt.PromptInfo.Builder().setTitle(title)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            info.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
        } else {
            @Suppress("DEPRECATION")
            info.setDeviceCredentialAllowed(true)
        }

        prompt.authenticate(info.build(), BiometricPrompt.CryptoObject(cipher))
    }

    /**
     * True when the user can prove identity at all: a biometric sensor, or a
     * secure screen lock (PIN / pattern / password) they can fall back to.
     */
    private fun authAvailable(): Boolean {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard?.isDeviceSecure == true) return true

        return BiometricManager.from(this)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun parseLocked(body: String): Boolean? = runCatching {
        JSONObject(body).optBoolean("locked", false)
    }.getOrNull()

    private fun detail(body: String): String = runCatching {
        JSONObject(body).optString("detail")
    }.getOrDefault("")

    private fun focusLabel(body: String): String? {
        val focus = runCatching { JSONObject(body).optJSONObject("focus") }.getOrNull()
            ?: return null
        val cls = focus.optString("class")
        val title = focus.optString("title")
        return when {
            cls.isBlank() -> null
            title.isBlank() -> cls
            else -> "$cls · $title"
        }
    }

    private fun setMessage(text: String) {
        state = state.copy(message = text)
    }

    companion object {
        const val EXTRA_ACTION = "action"
    }
}
