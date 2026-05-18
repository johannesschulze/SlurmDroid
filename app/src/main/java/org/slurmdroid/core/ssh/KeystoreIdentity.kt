package org.slurmdroid.core.ssh

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.jcraft.jsch.Identity
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAKeyGenParameterSpec

/**
 * JSch [Identity] backed by an RSA key pair in the Android Keystore.
 * The private key never leaves the hardware-backed Keystore; only signing operations are performed.
 *
 * Uses rsa-sha2-256 (SHA-256) to meet modern SSH server requirements (RFC 8332).
 */
class KeystoreIdentity(private val keyAlias: String) : Identity {

    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore")
        .also { it.load(null) }

    override fun getAlgName(): String = ALG_NAME
    override fun getName(): String = keyAlias
    override fun isEncrypted(): Boolean = false
    override fun setPassphrase(passphrase: ByteArray?): Boolean = true
    override fun decrypt(): Boolean = true
    override fun clear() {}

    override fun getPublicKeyBlob(): ByteArray {
        val cert = keyStore.getCertificate(keyAlias) ?: return ByteArray(0)
        val pub = cert.publicKey as? RSAPublicKey ?: return ByteArray(0)
        return buildSshBytes(
            "ssh-rsa".toByteArray(Charsets.UTF_8),
            pub.publicExponent.toByteArray(),
            pub.modulus.toByteArray(),
        )
    }

    override fun getSignature(data: ByteArray): ByteArray {
        val privateKey = keyStore.getKey(keyAlias, null) as? PrivateKey ?: return ByteArray(0)
        val rawSig = Signature.getInstance(SIGNATURE_ALG).run {
            initSign(privateKey)
            update(data)
            sign()
        }
        return buildSshBytes(ALG_NAME.toByteArray(Charsets.UTF_8), rawSig)
    }

    companion object {
        private const val ALG_NAME = "rsa-sha2-256"
        private const val SIGNATURE_ALG = "SHA256withRSA"
        const val DEFAULT_KEY_ALIAS = "slurmdroid_ssh_key"
        const val KEY_SIZE = 3072

        fun exists(alias: String = DEFAULT_KEY_ALIAS): Boolean =
            KeyStore.getInstance("AndroidKeyStore").run { load(null); containsAlias(alias) }

        /** Generates a new RSA key pair in the Android Keystore, replacing any existing key. */
        fun generate(alias: String = DEFAULT_KEY_ALIAS) {
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
                .apply {
                    initialize(
                        KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                            .setAlgorithmParameterSpec(RSAKeyGenParameterSpec(KEY_SIZE, RSAKeyGenParameterSpec.F4))
                            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                            .build()
                    )
                }
                .generateKeyPair()
        }

        /**
         * Returns the public key in OpenSSH format: `ssh-rsa <base64blob> SlurmDroid`
         * suitable for pasting into the server's authorized_keys.
         */
        fun getOpenSshPublicKey(alias: String = DEFAULT_KEY_ALIAS): String {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val pub = ks.getCertificate(alias)?.publicKey as? RSAPublicKey ?: return ""
            val blob = buildSshBytes(
                "ssh-rsa".toByteArray(Charsets.UTF_8),
                pub.publicExponent.toByteArray(),
                pub.modulus.toByteArray(),
            )
            return "ssh-rsa ${Base64.encodeToString(blob, Base64.NO_WRAP)} SlurmDroid"
        }

        fun buildSshBytes(vararg parts: ByteArray): ByteArray {
            val out = ByteArrayOutputStream()
            val dos = DataOutputStream(out)
            for (part in parts) { dos.writeInt(part.size); dos.write(part) }
            dos.flush()
            return out.toByteArray()
        }
    }
}
