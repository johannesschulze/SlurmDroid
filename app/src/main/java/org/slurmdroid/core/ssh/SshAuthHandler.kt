package org.slurmdroid.core.ssh

import com.jcraft.jsch.UIKeyboardInteractive
import com.jcraft.jsch.UserInfo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles JSch keyboard-interactive authentication.
 *
 * Cluster prompt order: OTP first, then password.
 * The OTP prompt is detected by keyword matching against known prompt strings.
 * Adjust OTP_KEYWORDS if the cluster uses a different prompt text.
 */
@Singleton
class SshAuthHandler @Inject constructor(
    private val credentialStore: SshCredentialStore,
    private val totpGenerator: TotpGenerator,
) : UserInfo, UIKeyboardInteractive {

    // ---  UIKeyboardInteractive  ---

    override fun promptKeyboardInteractive(
        destination: String,
        name: String,
        instruction: String,
        prompt: Array<String>,
        echo: BooleanArray,
    ): Array<String>? = try {
        Array(prompt.size) { i ->
            val p = prompt[i].lowercase()
            if (OTP_KEYWORDS.any { p.contains(it) }) {
                totpGenerator.generate(credentialStore.totpSeed) ?: return null
            } else {
                credentialStore.password
            }
        }
    } catch (_: Exception) {
        null // returning null signals auth failure to JSch
    }

    // ---  UserInfo  ---

    override fun getPassphrase(): String? = null
    override fun getPassword(): String = credentialStore.password
    override fun promptPassword(message: String): Boolean = true
    override fun promptPassphrase(message: String): Boolean = false
    override fun showMessage(message: String) {}

    // Accept the host key unconditionally (StrictHostKeyChecking=no is set in SshManager;
    // this callback is only reached when StrictHostKeyChecking=ask).
    override fun promptYesNo(message: String): Boolean = true

    companion object {
        /** Keywords used to identify the OTP challenge within a keyboard-interactive prompt. */
        private val OTP_KEYWORDS = listOf(
            "one-time", "otp", "totp", "verification code", "authenticator", "token",
        )
    }
}
