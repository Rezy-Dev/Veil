package dev.rezy.veil.crypto

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the desktop password under an Android Keystore key that can only be
 * used while the user has just authenticated with a biometric.
 *
 * The ciphertext is useless on its own: the AES key never leaves the TEE, and
 * every encrypt/decrypt requires a live fingerprint. Nothing here is readable
 * by a backup, an adb pull, or a stolen unlocked screen.
 */
object Vault {

    private const val ALIAS = "veil_desktop_password"
    private const val PREFS = "veil_vault"
    private const val KEY_CT = "ct"
    private const val KEY_IV = "iv"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hasPassword(context: Context): Boolean = prefs(context).contains(KEY_CT)

    private fun keyStore(): KeyStore =
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun secretKey(): SecretKey {
        val store = keyStore()
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }

        val generator =
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")

        val builder = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(
                0, KeyProperties.AUTH_BIOMETRIC_STRONG
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }

        generator.init(builder.build())
        return generator.generateKey()
    }

    /** Cipher ready to encrypt; must be handed to BiometricPrompt first. */
    fun cipherForEncrypt(): Cipher {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        return cipher
    }

    /** Cipher ready to decrypt the stored password; needs biometric unlock. */
    fun cipherForDecrypt(context: Context): Cipher {
        val iv = Base64.decode(
            prefs(context).getString(KEY_IV, null) ?: error("no saved password"),
            Base64.NO_WRAP
        )
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
        return cipher
    }

    /** Call only after the biometric prompt succeeded with this cipher. */
    fun store(context: Context, cipher: Cipher, password: String) {
        val ciphertext = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
        prefs(context).edit()
            .putString(KEY_CT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    /** Call only after the biometric prompt succeeded with this cipher. */
    fun readPassword(context: Context, cipher: Cipher): String {
        val ciphertext = Base64.decode(
            prefs(context).getString(KEY_CT, null) ?: error("no saved password"),
            Base64.NO_WRAP
        )
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
        runCatching { keyStore().deleteEntry(ALIAS) }
    }
}
