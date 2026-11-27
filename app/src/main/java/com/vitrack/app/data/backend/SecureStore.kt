package com.vitrack.app.data.backend

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Small secrets on the phone: the sign-in session and trainer link tokens. */
interface SecureStore {
    fun get(key: String): String?

    /** Null removes the value. */
    fun put(key: String, value: String?)
}

/**
 * Values encrypted with an AES key kept in the Android Keystore, stored in a
 * private preferences file that is left out of backups (res/xml rules), so a
 * session never moves to another phone.
 */
class KeystoreSecureStore(context: Context) : SecureStore {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun get(key: String): String? {
        val stored = prefs.getString(key, null) ?: return null
        return runCatching {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_SIZE))
            cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE).decodeToString()
        }.getOrElse {
            // A value this phone's key can't open (for example after a restore): forget it.
            prefs.edit().remove(key).apply()
            null
        }
    }

    override fun put(key: String, value: String?) {
        if (value == null) {
            prefs.edit().remove(key).apply()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.iv + cipher.doFinal(value.encodeToByteArray())
        prefs.edit().putString(key, Base64.encodeToString(sealed, Base64.NO_WRAP)).apply()
    }

    @Synchronized
    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val FILE = "secure"
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "vitrack_secure"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}
