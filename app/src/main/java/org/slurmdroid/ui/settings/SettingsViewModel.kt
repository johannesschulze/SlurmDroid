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
import org.slurmdroid.core.plugin.PluginManager
import org.slurmdroid.core.ssh.KeystoreIdentity
import org.slurmdroid.core.ssh.SshCredentialStore
import org.slurmdroid.core.ssh.SshManager
import org.slurmdroid.plugin.api.PluginSettingParcel
import javax.inject.Inject

data class PluginSettingsState(
    val pluginId: String,
    val displayName: String,
    val settings: List<PluginSettingParcel>,
    val values: Map<String, String>,
)

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
    val nnUNetBaseDir: String = "",
    val nnUNetResultsDir: String = "",
    val nnUNetRawDir: String = "",
    val nnUNetPreprocessedDir: String = "",
    val pluginStates: List<PluginSettingsState> = emptyList(),
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
    private val pluginManager: PluginManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            pluginManager.plugins.collect { plugins ->
                val pluginStates = plugins.map { plugin ->
                    PluginSettingsState(
                        pluginId = plugin.id,
                        displayName = plugin.displayName,
                        settings = plugin.settings,
                        values = plugin.settings.associate { s ->
                            s.key to appPreferences.getPluginSetting(plugin.id, s.key, s.defaultText)
                        },
                    )
                }
                _uiState.update { it.copy(pluginStates = pluginStates) }
            }
        }
    }

    // ── field updates ──────────────────────────────────────────────────────────

    fun onHostname(v: String) { _uiState.update { it.copy(hostname = v) }; autoSave() }
    fun onPort(v: String) { _uiState.update { it.copy(port = v.filter(Char::isDigit).take(5)) }; autoSave() }
    fun onUsername(v: String) { _uiState.update { it.copy(username = v) }; autoSave() }
    fun onPassword(v: String) { _uiState.update { it.copy(password = v) }; autoSave() }
    fun onTotpSeed(v: String) { _uiState.update { it.copy(totpSeed = v.trim().uppercase()) }; autoSave() }

    fun onLogDirectory(v: String) { _uiState.update { it.copy(logDirectory = v) }; autoSave() }

    fun onNnUNetBaseDir(v: String) {
        _uiState.update { state ->
            val oldBase = state.nnUNetBaseDir
            state.copy(
                nnUNetBaseDir = v,
                nnUNetResultsDir = autoFill(state.nnUNetResultsDir, oldBase, v, "results"),
                nnUNetRawDir = autoFill(state.nnUNetRawDir, oldBase, v, "raw"),
                nnUNetPreprocessedDir = autoFill(state.nnUNetPreprocessedDir, oldBase, v, "preprocessed"),
            )
        }
        autoSave()
    }

    fun onNnUNetResultsDir(v: String) { _uiState.update { it.copy(nnUNetResultsDir = v) }; autoSave() }
    fun onNnUNetRawDir(v: String) { _uiState.update { it.copy(nnUNetRawDir = v) }; autoSave() }
    fun onNnUNetPreprocessedDir(v: String) { _uiState.update { it.copy(nnUNetPreprocessedDir = v) }; autoSave() }

    /** Updates a subdir if it is blank or still equals the auto-computed value from the old base. */
    private fun autoFill(current: String, oldBase: String, newBase: String, sub: String): String {
        val wasAuto = current.isBlank() || current == nnUNetSubPath(oldBase, sub)
        return if (wasAuto) nnUNetSubPath(newBase, sub) else current
    }

    private fun nnUNetSubPath(base: String, sub: String): String =
        if (base.isBlank()) "" else "${base.trimEnd('/')}/$sub"

    fun onShowRunningNotifications(v: Boolean) {
        appPreferences.showRunningJobNotifications = v
        if (!v) jobNotificationManager.cancelAllRunningNotifications()
        _uiState.update { it.copy(showRunningNotifications = v) }
    }

    fun onPluginSetting(pluginId: String, key: String, value: String) {
        appPreferences.setPluginSetting(pluginId, key, value)
        _uiState.update { state ->
            val updated = state.pluginStates.map { ps ->
                if (ps.pluginId == pluginId) ps.copy(values = ps.values + (key to value)) else ps
            }
            state.copy(pluginStates = updated)
        }
        pluginManager.notifySettingsChanged(pluginId)
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
            appPreferences.nnUNetBaseDir = nnUNetBaseDir.trim()
            appPreferences.nnUNetResultsDir = nnUNetResultsDir.trim()
            appPreferences.nnUNetRawDir = nnUNetRawDir.trim()
            appPreferences.nnUNetPreprocessedDir = nnUNetPreprocessedDir.trim()
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
                nnUNetBaseDir = appPreferences.nnUNetBaseDir,
                nnUNetResultsDir = appPreferences.nnUNetResultsDir,
                nnUNetRawDir = appPreferences.nnUNetRawDir,
                nnUNetPreprocessedDir = appPreferences.nnUNetPreprocessedDir,
            )
        }
    }
}
