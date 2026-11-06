package dev.rezy.veil.net

import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Talks to the desktop daemon over Tailscale.
 *
 * Every request carries a timestamp, a random nonce and an HMAC-SHA256 over
 *   ts \n nonce \n method \n path \n body
 * so a replay or a forged request is rejected by the daemon. Tailscale provides
 * transport encryption; the MAC is what proves the request came from this app.
 */
object VeilClient {

    data class Response(val ok: Boolean, val code: Int, val body: String)

    fun call(
        host: String,
        port: Int,
        secret: String,
        method: String,
        path: String,
        body: String = ""
    ): Response {
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val nonce = randomNonce()
        val signed = "$timestamp\n$nonce\n$method\n$path\n$body"
        val signature = hmacSha256(secret, signed)

        val connection = (URL("http://$host:$port$path").openConnection()
                as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 6000
            readTimeout = 20000
            doInput = true
            setRequestProperty("X-Veil-Ts", timestamp)
            setRequestProperty("X-Veil-Nonce", nonce)
            setRequestProperty("X-Veil-Sig", signature)
            if (body.isNotEmpty()) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }

        return try {
            if (body.isNotEmpty()) {
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            Response(code in 200..299, code, text)
        } finally {
            connection.disconnect()
        }
    }

    private fun hmacSha256(secret: String, data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8)).toHex()
    }

    private fun randomNonce(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.toHex()
    }

    private fun ByteArray.toHex(): String {
        val out = StringBuilder(size * 2)
        for (b in this) out.append("%02x".format(b))
        return out.toString()
    }
}
