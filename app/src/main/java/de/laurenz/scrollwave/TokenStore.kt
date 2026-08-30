package de.laurenz.scrollwave

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class OAuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long,
)

class TokenStore(context: Context) {
    private val preferences = context.getSharedPreferences("secure_auth", Context.MODE_PRIVATE)

    fun load(): OAuthTokens? = runCatching {
        val payload = preferences.getString(KEY_PAYLOAD, null) ?: return null
        val iv = preferences.getString(KEY_IV, null) ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)),
            )
        }
        val json = JSONObject(String(cipher.doFinal(Base64.decode(payload, Base64.NO_WRAP))))
        OAuthTokens(
            accessToken = json.getString("access"),
            refreshToken = json.getString("refresh"),
            expiresAtMillis = json.getLong("expires"),
        )
    }.getOrElse {
        clear()
        null
    }

    fun save(tokens: OAuthTokens) {
        val plainText = JSONObject()
            .put("access", tokens.accessToken)
            .put("refresh", tokens.refreshToken)
            .put("expires", tokens.expiresAtMillis)
            .toString()
            .toByteArray()
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, secretKey())
        }
        preferences.edit()
            .putString(KEY_PAYLOAD, Base64.encodeToString(cipher.doFinal(plainText), Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun saveOAuthState(state: String) = preferences.edit().putString(KEY_STATE, state).apply()

    fun consumeOAuthState(): String? {
        val value = preferences.getString(KEY_STATE, null)
        preferences.edit().remove(KEY_STATE).apply()
        return value
    }

    fun clear() = preferences.edit().clear().apply()

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val KEY_ALIAS = "scrollwave-oauth"
        const val KEY_PAYLOAD = "payload"
        const val KEY_IV = "iv"
        const val KEY_STATE = "oauth_state"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
