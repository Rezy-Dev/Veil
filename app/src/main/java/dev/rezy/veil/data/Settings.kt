package dev.rezy.veil.data

import android.content.Context

/** Connection settings for the desktop daemon. */
class Settings(context: Context) {

    private val sp = context.getSharedPreferences("veil_remote", Context.MODE_PRIVATE)

    var host: String
        get() = sp.getString("host", "") ?: ""
        set(value) = sp.edit().putString("host", value).apply()

    var port: Int
        get() = sp.getInt("port", 8765)
        set(value) = sp.edit().putInt("port", value).apply()

    var secret: String
        get() = sp.getString("secret", "") ?: ""
        set(value) = sp.edit().putString("secret", value).apply()

    val isConfigured: Boolean
        get() = host.isNotBlank() && secret.isNotBlank()

    val endpoint: String
        get() = "$host:$port"
}
