package org.slurmdroid.core.ssh

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSchException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slurmdroid.core.Result
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommandExecutor @Inject constructor(
    private val sshManager: SshManager,
) {
    /**
     * Executes [command] on the cluster and returns trimmed stdout on success.
     * The SSH session is reused across calls and reconnected automatically if dropped.
     */
    suspend fun execute(command: String): Result<String> {
        val sessionResult = sshManager.getSession()
        if (sessionResult !is Result.Success) return sessionResult as Result<String>
        val result = withContext(Dispatchers.IO) { runCommand(command, sessionResult.data) }
        if (result !is Result.ConnectionError) return result
        // Session died mid-command — evict the stale session and retry once with a fresh connection.
        sshManager.disconnect()
        val retrySession = sshManager.getSession()
        if (retrySession !is Result.Success) return retrySession as Result<String>
        return withContext(Dispatchers.IO) { runCommand(command, retrySession.data) }
    }

    private fun runCommand(command: String, session: com.jcraft.jsch.Session): Result<String> {
        val channel = session.openChannel("exec") as ChannelExec
        return try {
            val stdout = ByteArrayOutputStream()
            val stderr = ByteArrayOutputStream()
            channel.setCommand(command)
            channel.outputStream = stdout
            channel.setErrStream(stderr)
            channel.connect(CHANNEL_TIMEOUT_MS)

            // Poll until the channel closes (command finished)
            while (!channel.isClosed) Thread.sleep(POLL_INTERVAL_MS)

            val out = stdout.toString(Charsets.UTF_8.name()).trim()
            val err = stderr.toString(Charsets.UTF_8.name()).trim()
            val exit = channel.exitStatus

            when {
                exit == 0 -> Result.Success(out)
                out.isNotBlank() -> Result.Success(out) // some commands write to stdout even on non-zero exit
                err.isNotBlank() -> Result.UnknownError("Command failed (exit $exit): $err")
                else -> Result.UnknownError("Command failed with exit code $exit")
            }
        } catch (e: JSchException) {
            Result.ConnectionError(e.message ?: "Channel error")
        } catch (e: Exception) {
            Result.UnknownError(e.message ?: "Execution error", e)
        } finally {
            channel.disconnect()
        }
    }

    companion object {
        private const val CHANNEL_TIMEOUT_MS = 10_000
        private const val POLL_INTERVAL_MS = 100L
    }
}
