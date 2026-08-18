package com.example.douyinautomation.automation

import android.content.Context
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

/** Basic app-side encryption for the small authorization config; the token is never logged. */
class SecureAuthStore(
    context: Context,
    private val preferencesName: String = "secure_auth_config",
) {
    private val preferences = context.applicationContext
        .getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    fun save(config: AuthConfig): Boolean = runCatching {
        require(config.isUsable()) { "Invalid authorization config" }
        val plaintext = JSONObject().apply {
            put("endpoint", config.endpoint)
            put("license_token", config.licenseToken)
            put("device_id", config.deviceId)
        }.toString().toByteArray(StandardCharsets.UTF_8)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val payload = buildString {
            append(encode(cipher.iv))
            append(':')
            append(encode(cipher.doFinal(plaintext)))
        }
        preferences.edit().putString(CONFIG_KEY, payload).commit()
    }.getOrDefault(false)

    fun read(): AuthConfig? = runCatching {
        val payload = preferences.getString(CONFIG_KEY, null) ?: return null
        val parts = payload.split(':', limit = 2)
        if (parts.size != 2) return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, decode(parts[0])),
        )
        val json = JSONObject(String(cipher.doFinal(decode(parts[1])), StandardCharsets.UTF_8))
        AuthConfig(
            endpoint = json.getString("endpoint"),
            licenseToken = json.getString("license_token"),
            deviceId = json.getString("device_id"),
        ).takeIf(AuthConfig::isUsable)
    }.getOrNull()

    fun clear() {
        preferences.edit().remove(CONFIG_KEY).apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null)
        if (existing is SecretKey) return existing
        val generator = KeyGenerator.getInstance(KEY_ALGORITHM, ANDROID_KEYSTORE)
        generator.init(android.security.keystore.KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
        ).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .build())
        return generator.generateKey()
    }

    private fun encode(value: ByteArray): String = Base64.encodeToString(value, Base64.NO_WRAP)
    private fun decode(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "douyin_automation_auth_key"
        const val KEY_ALGORITHM = "AES"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
        const val CONFIG_KEY = "encrypted_config"
    }
}
