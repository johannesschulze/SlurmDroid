package org.slurmdroid.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.slurmdroid.core.AppPreferences
import org.slurmdroid.core.Result
import org.slurmdroid.core.notifications.JobNotificationManager
import org.slurmdroid.core.ssh.KeystoreIdentity
import org.slurmdroid.core.ssh.SshCredentialStore
import org.slurmdroid.core.ssh.SshManager
import javax.inject.Inject

data class SettingsUiState(
    val hostname: String = "",
    val port: String = "22",
    val username: String = "",
    val password: String = "",
    val totpSeed: String = "",
    val hasKey: Boolean = false,
    val publicKeyText: String = "",
    val connectionTest: ConnectionTestState = ConnectionTestState.Idle,
    val showRunningNotifications: Boolean = true,
    val logDirectory: String = "slurm_logs",
)

sealed class ConnectionTestState {
    object Idle : ConnectionTestState()
    object Testing : ConnectionTestState()
    data class Success(val info: String) : ConnectionTestState()
    data class Failure(val error: String) : ConnectionTestState()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val credentialStore: SshCredentialStore,
    private val sshManager: SshManager,
    private val appPreferences: AppPreferences,
    private val jobNotificationManager: JobNotificationManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    // ── field updates ──────────────────────────────────────────────────────────

    fun onHostname(v: String) { _uiState.update { it.copy(hostname = v) }; autoSave() }
    fun onPort(v: String) { _uiState.update { it.copy(port = v.filter(Char::isDigit).take(5)) }; autoSave() }
    fun onUsername(v: String) { _uiState.update { it.copy(username = v) }; autoSave() }
    fun onPassword(v: String) { _uiState.update { it.copy(password = v) }; autoSave() }
    fun onTotpSeed(v: String) { _uiState.update { it.copy(totpSeed = v.trim().uppercase()) }; autoSave() }

    fun onLogDirectory(v: String) { _uiState.update { it.copy(logDirectory = v) }; autoSave() }

    fun onShowRunningNotifications(v: Boolean) {
        appPreferences.showRunningJobNotifications = v
        if (!v) jobNotificationManager.cancelAllRunningNotifications()
        _uiState.update { it.copy(showRunningNotifications = v) }
    }

    /** Parses an `otpauth://totp/…?secret=BASE32&…` URI (from QR scan) and extracts the secret. */
    fun onTotpFromQr(uri: String) {
        val secret = Regex("[?&]secret=([^&]+)", RegexOption.IGNORE_CASE)
            .find(uri)?.groupValues?.get(1) ?: return
        _uiState.update { it.copy(totpSeed = secret.trim().uppercase()) }
        autoSave()
    }

    // ── actions ────────────────────────────────────────────────────────────────

    private var saveJob: Job? = null
    private fun autoSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch { delay(800); save() }
    }

    private fun save() {
        with(_uiState.value) {
            credentialStore.hostname = hostname.trim()
            credentialStore.port = port.toIntOrNull() ?: 22
            credentialStore.username = username.trim()
            credentialStore.password = password
            credentialStore.totpSeed = totpSeed
            appPreferences.logDirectory = logDirectory.trim()
        }
        _uiState.update { it.copy(connectionTest = ConnectionTestState.Idle) }
    }

    fun generateKey() {
        viewModelScope.launch(Dispatchers.IO) {
            KeystoreIdentity.generate()
            credentialStore.keyAlias = KeystoreIdentity.DEFAULT_KEY_ALIAS
            val pubKey = KeystoreIdentity.getOpenSshPublicKey()
            _uiState.update { it.copy(hasKey = true, publicKeyText = pubKey) }
        }
    }

    fun testConnection() {
        save()
        _uiState.update { it.copy(connectionTest = ConnectionTestState.Testing) }
        viewModelScope.launch {
            sshManager.disconnect()
            val state = when (val r = sshManager.getSession()) {
                is Result.Success -> ConnectionTestState.Success(
                    "Connected to ${credentialStore.hostname}"
                )
                is Result.AuthError -> ConnectionTestState.Failure("Auth failed: ${r.message}")
                is Result.ConnectionError -> ConnectionTestState.Failure("Connection failed: ${r.message}")
                else -> ConnectionTestState.Failure("Unknown error")
            }
            _uiState.update { it.copy(connectionTest = state) }
        }
    }

    // ── internals ──────────────────────────────────────────────────────────────

    private fun load() {
        val alias = credentialStore.keyAlias
        val hasKey = alias.isNotBlank() && KeystoreIdentity.exists(alias)
        _uiState.update {
            it.copy(
                hostname = credentialStore.hostname,
                port = credentialStore.port.toString(),
                username = credentialStore.username,
                password = credentialStore.password,
                totpSeed = credentialStore.totpSeed,
                hasKey = hasKey,
                publicKeyText = if (hasKey) KeystoreIdentity.getOpenSshPublicKey(alias) else "",
                showRunningNotifications = appPreferences.showRunningJobNotifications,
                logDirectory = appPreferences.logDirectory,
            )
        }
    }
}
