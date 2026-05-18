package org.slurmdroid.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showQrScanner by remember { mutableStateOf(false) }

    if (showQrScanner) {
        QrScannerScreen(
            onScanned = { uri ->
                viewModel.onTotpFromQr(uri)
                showQrScanner = false
            },
            onDismiss = { showQrScanner = false },
        )
        return
    }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.savedBanner) {
        if (state.savedBanner) snackbar.showSnackbar("Saved")
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // ── SSH Connection ────────────────────────────────────────────────
            SectionHeader("SSH Connection")
            OutlinedTextField(
                value = state.hostname,
                onValueChange = viewModel::onHostname,
                label = { Text("Hostname") },
                placeholder = { Text("uc3.scc.kit.edu") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.port,
                    onValueChange = viewModel::onPort,
                    label = { Text("Port") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(0.3f),
                )
                OutlinedTextField(
                    value = state.username,
                    onValueChange = viewModel::onUsername,
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.weight(0.7f),
                )
            }

            Spacer(Modifier.height(4.dp))

            // ── Authentication ─────────────────────────────────────────────────
            SectionHeader("Authentication")
            var showPassword by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::onPassword,
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None
                                       else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle password visibility",
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(4.dp))

            // ── TOTP ──────────────────────────────────────────────────────────
            SectionHeader("TOTP (One-Time Password)")
            OutlinedTextField(
                value = state.totpSeed,
                onValueChange = viewModel::onTotpSeed,
                label = { Text("TOTP Secret (Base32)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = { showQrScanner = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp))
                Text("Scan QR Code")
            }

            Spacer(Modifier.height(4.dp))

            // ── SSH Key ───────────────────────────────────────────────────────
            SectionHeader("SSH Key (optional)")
            Text(
                if (state.hasKey) "Key generated — copy the public key and add it to ~/.ssh/authorized_keys on the cluster."
                else "No key generated yet. Generate one to enable password-free reconnects.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(
                onClick = viewModel::generateKey,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Key, contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp))
                Text(if (state.hasKey) "Regenerate SSH Key" else "Generate SSH Key")
            }
            AnimatedVisibility(state.publicKeyText.isNotBlank()) {
                val context = LocalContext.current
                PublicKeyCard(
                    publicKey = state.publicKeyText,
                    onCopy = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("SSH public key", state.publicKeyText))
                    },
                )
            }

            Spacer(Modifier.height(4.dp))

            // ── Connection Test ───────────────────────────────────────────────
            SectionHeader("Connection")
            Button(
                onClick = viewModel::testConnection,
                enabled = state.connectionTest !is ConnectionTestState.Testing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.connectionTest is ConnectionTestState.Testing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(Icons.Default.Wifi, contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp))
                    Text("Test Connection")
                }
            }
            when (val test = state.connectionTest) {
                is ConnectionTestState.Success -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Default.Check, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary)
                    Text(test.info, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary)
                }
                is ConnectionTestState.Failure -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Default.WifiOff, contentDescription = null,
                        tint = MaterialTheme.colorScheme.error)
                    Text(test.error, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error)
                }
                else -> {}
            }

            Spacer(Modifier.height(4.dp))

            // ── Save ─────────────────────────────────────────────────────────
            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save")
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary)
        HorizontalDivider(modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
    }
}

@Composable
private fun PublicKeyCard(publicKey: String, onCopy: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            publicKey,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth(),
            maxLines = 4,
        )
        OutlinedButton(onClick = onCopy, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.ContentCopy, contentDescription = null,
                modifier = Modifier.padding(end = 8.dp))
            Text("Copy Public Key")
        }
    }
}
