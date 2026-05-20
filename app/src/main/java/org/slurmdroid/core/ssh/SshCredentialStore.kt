package org.slurmdroid.core.ssh

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SshCredentialStore @Inject constructor(@ApplicationContext context: Context) {

    private val prefs: SharedPreferences = try {
        createPrefs(context)
    } catch (_: Exception) {
        // Corrupted or incompatible keyset (e.g. after reinstall or Keystore invalidation).
        // Wipe and start fresh — user will need to re-enter credentials.
        context.deleteSharedPreferences(PREFS_FILE)
        createPrefs(context)
    }

    var hostname: String
        get() = prefs.getString(KEY_HOSTNAME, "") ?: ""
        set(v) { prefs.edit().putString(KEY_HOSTNAME, v).commit() }

    var port: Int
        get() = prefs.getInt(KEY_PORT, 22)
        set(v) { prefs.edit().putInt(KEY_PORT, v).commit() }

    var username: String
        get() = prefs.getString(KEY_USERNAME, "") ?: ""
        set(v) { prefs.edit().putString(KEY_USERNAME, v).commit() }

    var password: String
        get() = prefs.getString(KEY_PASSWORD, "") ?: ""
        set(v) { prefs.edit().putString(KEY_PASSWORD, v).commit() }

    /** Base32-encoded TOTP secret — never stored in plaintext outside EncryptedSharedPreferences. */
    var totpSeed: String
        get() = prefs.getString(KEY_TOTP_SEED, "") ?: ""
        set(v) { prefs.edit().putString(KEY_TOTP_SEED, v).commit() }

    /** Alias of the RSA key pair in the Android Keystore; empty if no key has been generated yet. */
    var keyAlias: String
        get() = prefs.getString(KEY_ALIAS, "") ?: ""
        set(v) { prefs.edit().putString(KEY_ALIAS, v).commit() }

    fun isConfigured(): Boolean =
        hostname.isNotBlank() && username.isNotBlank() && password.isNotBlank()

    companion object {
        private const val PREFS_FILE = "slurmdroid_ssh_creds"
        private const val KEY_HOSTNAME = "hostname"
        private const val KEY_PORT = "port"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_TOTP_SEED = "totp_seed"
        private const val KEY_ALIAS = "key_alias"

        private fun createPrefs(context: Context): SharedPreferences =
            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE,
                MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
    }
}
