package de.markusressel.mkdocseditor.auth.token

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts and decrypts stored tokens
 */
interface TokenCipher {
    fun encrypt(plain: ByteArray): ByteArray

    /**
     * @throws java.security.GeneralSecurityException if the data cannot be decrypted (wrong key, tampered data)
     */
    fun decrypt(data: ByteArray): ByteArray
}

/**
 * AES-GCM with a random IV per message. The output is the 12 byte IV followed by the ciphertext and tag.
 *
 * @param keyProvider returns the key, see [AndroidKeystoreKey]
 */
class AesGcmTokenCipher(
    private val keyProvider: () -> SecretKey,
) : TokenCipher {

    override fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // the Android Keystore refuses caller-provided IVs by default, so let the cipher pick one
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider())
        val iv = cipher.iv
        check(iv.size == IV_LENGTH) { "unexpected IV length ${iv.size}" }
        return iv + cipher.doFinal(plain)
    }

    override fun decrypt(data: ByteArray): ByteArray {
        require(data.size > IV_LENGTH) { "data too short" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            keyProvider(),
            GCMParameterSpec(TAG_LENGTH_BITS, data, 0, IV_LENGTH)
        )
        return cipher.doFinal(data, IV_LENGTH, data.size - IV_LENGTH)
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH = 12
        private const val TAG_LENGTH_BITS = 128
    }
}

/**
 * A non-exportable AES key in the Android Keystore. It is created on first use.
 */
object AndroidKeystoreKey {
    private const val PROVIDER = "AndroidKeyStore"

    @Synchronized
    fun getOrCreate(alias: String): SecretKey {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }
}
