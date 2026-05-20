package org.slurmdroid.core.ssh

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges the synchronous JSch auth callback to the UI when no TOTP seed is configured.
 *
 * [requestOtp] blocks the calling thread (via runBlocking in SshAuthHandler) until the
 * user submits an OTP in the dialog observed via [isPending].
 */
@Singleton
class ManualOtpRequest @Inject constructor() {

    private val _isPending = MutableStateFlow(false)
    val isPending: StateFlow<Boolean> = _isPending.asStateFlow()

    private var deferred: CompletableDeferred<String>? = null

    suspend fun requestOtp(): String {
        val d = CompletableDeferred<String>()
        deferred = d
        _isPending.value = true
        return try {
            d.await()
        } finally {
            _isPending.value = false
            deferred = null
        }
    }

    fun submit(otp: String) {
        deferred?.complete(otp)
    }

    fun cancel() {
        deferred?.cancel()
        deferred = null
        _isPending.value = false
    }
}
