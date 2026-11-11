package com.oxlo.gallary.data.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Passwords and OAuth tokens never touch the Room database. They live in
 * EncryptedSharedPreferences keyed by source id.
 */
class CredentialStore(context: Context) {

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences by lazy {
        runCatching {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }.getOrElse {
            appContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        }
    }

    fun savePassword(sourceId: Long, password: String) {
        prefs.edit().putString("password_$sourceId", password).apply()
    }

    fun password(sourceId: Long): String? = prefs.getString("password_$sourceId", null)

    fun saveToken(sourceId: Long, token: String) {
        prefs.edit().putString("token_$sourceId", token).apply()
    }

    fun token(sourceId: Long): String? = prefs.getString("token_$sourceId", null)

    fun clear(sourceId: Long) {
        prefs.edit().remove("password_$sourceId").remove("token_$sourceId").apply()
    }

    companion object {
        private const val FILE_NAME = "gallary_credentials"
    }
}
