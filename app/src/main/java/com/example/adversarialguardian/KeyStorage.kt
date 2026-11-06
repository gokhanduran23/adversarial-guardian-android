package com.example.adversarialguardian

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * API anahtarını cihazda ŞİFRELİ saklar.
 *
 * EncryptedSharedPreferences, anahtarı cihazın donanım destekli güvenli
 * deposuyla (Android Keystore) şifreler. Yani anahtar diske düz metin olarak
 * yazılmaz; cihaz çalınsa bile (kilitliyse) korunur.
 *
 * Not: bu kütüphane Google tarafından "deprecated" işaretli ama 1.1.0 stable'da
 * çalışıyor ve güvenli anahtar saklamanın en pratik yolu.
 */
object KeyStorage {
    private const val PREFS_NAME = "secure_prefs"
    private const val KEY_API = "api_key"

    private fun prefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveApiKey(context: Context, key: String) {
        prefs(context).edit().putString(KEY_API, key).apply()
    }

    fun loadApiKey(context: Context): String? {
        return prefs(context).getString(KEY_API, null)
    }

    fun clearApiKey(context: Context) {
        prefs(context).edit().remove(KEY_API).apply()
    }
}