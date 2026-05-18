package org.slurmdroid.core.ssh

import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.slurmdroid.core.Result
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SshManager @Inject constructor(
    private val credentialStore: SshCredentialStore,
    private val authHandler: SshAuthHandler,
) {
    private var session: Session? = null
    private val mutex = Mutex()

    /**
     * Returns an active [Session], connecting if necessary.
     *
     * Reconnect strategy (per spec):
     * 1. Try public-key auth if a key alias is stored in the Keystore.
     * 2. On failure (or no key), fall back to keyboard-interactive (OTP → TOTP, then password).
     */
    suspend fun getSession(): Result<Session> = mutex.withLock {
        val s = session
        if (s != null && s.isConnected) return@withLock Result.Success(s)
        connect()
    }

    suspend fun disconnect() = mutex.withLock {
        session?.disconnect()
        session = null
    }

    fun isConnected(): Boolean = session?.isConnected == true

    private suspend fun connect(): Result<Session> = withContext(Dispatchers.IO) {
        if (!credentialStore.isConfigured()) {
            return@withContext Result.ConnectionError("SSH credentials not configured")
        }

        val keyAlias = credentialStore.keyAlias
        if (keyAlias.isNotBlank()) {
            val keyResult = tryKeyAuth(keyAlias)
            if (keyResult is Result.Success) {
                session = keyResult.data
                return@withContext keyResult
            }
        }

        val kbdResult = tryKeyboardInteractiveAuth()
        if (kbdResult is Result.Success) session = kbdResult.data
        kbdResult
    }

    private fun tryKeyAuth(keyAlias: String): Result<Session> = try {
        val jsch = JSch()
        jsch.addIdentity(KeystoreIdentity(keyAlias), null)
        val s = newSession(jsch)
        s.setConfig("PreferredAuthentications", "publickey")
        s.connect(CONNECTION_TIMEOUT_MS)
        Result.Success(s)
    } catch (e: JSchException) {
        mapException(e)
    } catch (e: Exception) {
        Result.UnknownError(e.message ?: "Key auth error", e)
    }

    private fun tryKeyboardInteractiveAuth(): Result<Session> = try {
        val jsch = JSch()
        val s = newSession(jsch)
        s.setConfig("PreferredAuthentications", "keyboard-interactive")
        s.userInfo = authHandler
        s.connect(CONNECTION_TIMEOUT_MS)
        Result.Success(s)
    } catch (e: JSchException) {
        mapException(e)
    }

    private fun newSession(jsch: JSch): Session =
        jsch.getSession(credentialStore.username, credentialStore.hostname, credentialStore.port)
            .also {
                // TODO (Settings step): implement TOFU host key verification and persist to EncryptedSharedPreferences
                it.setConfig("StrictHostKeyChecking", "no")
            }

    private fun mapException(e: JSchException): Result<Nothing> {
        val msg = e.message ?: "Unknown SSH error"
        return when {
            msg.contains("Auth fail", ignoreCase = true) ||
                msg.contains("authentication", ignoreCase = true) -> Result.AuthError(msg)

            msg.contains("timeout", ignoreCase = true) ||
                msg.contains("Connection refused", ignoreCase = true) ||
                msg.contains("No route to host", ignoreCase = true) -> Result.ConnectionError(msg)

            else -> Result.UnknownError(msg, e)
        }
    }

    companion object {
        private const val CONNECTION_TIMEOUT_MS = 30_000
    }
}
