package org.slurmdroid.core.ssh

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator
import java.time.Instant
import java.util.Locale
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject

class TotpGenerator @Inject constructor() {

    private val generator = TimeBasedOneTimePasswordGenerator()

    /**
     * Generates a TOTP code for the given Base32-encoded secret seed.
     * Returns null if the seed is invalid or TOTP generation fails.
     */
    fun generate(base32Seed: String): String? = try {
        val keyBytes = Base32.decode(base32Seed)
        val key = SecretKeySpec(keyBytes, generator.algorithm)
        val otp = generator.generateOneTimePassword(key, Instant.now())
        String.format(Locale.ROOT, "%0${generator.passwordLength}d", otp)
    } catch (_: Exception) {
        null
    }
}

/** Minimal RFC 4648 Base32 decoder for TOTP seeds (no padding required). */
private object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun decode(input: String): ByteArray {
        val normalized = input.trimEnd('=').uppercase().filter { it in ALPHABET }
        var buffer = 0
        var bitsLeft = 0
        val result = mutableListOf<Byte>()
        for (ch in normalized) {
            buffer = (buffer shl 5) or ALPHABET.indexOf(ch)
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                result += ((buffer shr bitsLeft) and 0xFF).toByte()
            }
        }
        return result.toByteArray()
    }
}
