package org.slurmdroid.core.ssh

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SshCredentialStore @Inject constructor(@ApplicationContext context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "slurmdroid_ssh_creds",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var hostname: String
        get() = prefs.getString(KEY_HOSTNAME, "") ?: ""
        set(v) = prefs.edit().putString(KEY_HOSTNAME, v).apply()

    var port: Int
        get() = prefs.getInt(KEY_PORT, 22)
        set(v) = prefs.edit().putInt(KEY_PORT, v).apply()

    var username: String
        get() = prefs.getString(KEY_USERNAME, "") ?: ""
        set(v) = prefs.edit().putString(KEY_USERNAME, v).apply()

    var password: String
        get() = prefs.getString(KEY_PASSWORD, "") ?: ""
        set(v) = prefs.edit().putString(KEY_PASSWORD, v).apply()

    /** Base32-encoded TOTP secret — never stored in plaintext outside EncryptedSharedPreferences. */
    var totpSeed: String
        get() = prefs.getString(KEY_TOTP_SEED, "") ?: ""
        set(v) = prefs.edit().putString(KEY_TOTP_SEED, v).apply()

    /** Alias of the RSA key pair in the Android Keystore; empty if no key has been generated yet. */
    var keyAlias: String
        get() = prefs.getString(KEY_ALIAS, "") ?: ""
        set(v) = prefs.edit().putString(KEY_ALIAS, v).apply()

    fun isConfigured(): Boolean =
        hostname.isNotBlank() && username.isNotBlank() &&
            password.isNotBlank() && totpSeed.isNotBlank()

    companion object {
        private const val KEY_HOSTNAME = "hostname"
        private const val KEY_PORT = "port"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_TOTP_SEED = "totp_seed"
        private const val KEY_ALIAS = "key_alias"
    }
}
