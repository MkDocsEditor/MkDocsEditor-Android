package de.markusressel.mkdocseditor.auth

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/**
 * A passkey provider in software, for live tests: it answers WebAuthn creation and request
 * options (attestation "none", discoverable, user verification) with the JSON that Android
 * Credential Manager returns. A port of passkeytest.Authenticator of the backend.
 *
 * @param origin the origin in clientDataJSON; for the app `android:apk-key-hash:<hash>`
 */
class SoftwareAuthenticator(
    private val rpId: String,
    private val origin: String,
    private val aaguid: ByteArray = BITWARDEN_AAGUID,
) {
    class Key(val id: ByteArray, val userHandle: ByteArray, val keyPair: KeyPair, var counter: Int = 0)

    val keys = mutableListOf<Key>()

    private val random = SecureRandom()
    private val mapAdapter = Moshi.Builder().build()
        .adapter<Map<String, Any?>>(Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java))

    /** @return the RegistrationResponseJSON for PublicKeyCredentialCreationOptions */
    fun create(optionsJson: String): String {
        val options = requireNotNull(mapAdapter.fromJson(optionsJson))
        @Suppress("UNCHECKED_CAST")
        val rp = options["rp"] as Map<String, Any?>
        check(rp["id"] == rpId) { "rp id ${rp["id"]}, authenticator is for $rpId" }
        @Suppress("UNCHECKED_CAST")
        val user = options["user"] as Map<String, Any?>
        val keyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val key = Key(ByteArray(16).also(random::nextBytes), b64decode(user["id"] as String), keyPair)
        keys += key

        val clientData = json(mapOf("type" to "webauthn.create", "challenge" to options["challenge"], "origin" to origin, "crossOrigin" to false))
        val attestation = Cbor.encode(mapOf("fmt" to "none", "attStmt" to emptyMap<String, Any>(), "authData" to authData(key, 0)))
        return json(
            mapOf(
                "id" to b64(key.id), "rawId" to b64(key.id), "type" to "public-key",
                "authenticatorAttachment" to "platform",
                "response" to mapOf(
                    "clientDataJSON" to b64(clientData.toByteArray()),
                    "attestationObject" to b64(attestation),
                    "transports" to listOf("internal", "hybrid"),
                ),
                "clientExtensionResults" to emptyMap<String, Any>(),
            )
        )
    }

    /** @return the AuthenticationResponseJSON for PublicKeyCredentialRequestOptions */
    fun get(optionsJson: String): String {
        val options = requireNotNull(mapAdapter.fromJson(optionsJson))
        check(options["rpId"] == rpId) { "rp id ${options["rpId"]}, authenticator is for $rpId" }
        @Suppress("UNCHECKED_CAST")
        val allowed = (options["allowCredentials"] as? List<Map<String, Any?>>).orEmpty().map { it["id"] as String }
        val key = keys.lastOrNull { allowed.isEmpty() || b64(it.id) in allowed } ?: error("NotAllowedError: no matching passkey")

        key.counter++
        val clientData = json(mapOf("type" to "webauthn.get", "challenge" to options["challenge"], "origin" to origin, "crossOrigin" to false))
            .toByteArray()
        val authData = authData(null, key.counter)
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(key.keyPair.private)
            update(authData)
            update(sha256(clientData))
            sign()
        }
        return json(
            mapOf(
                "id" to b64(key.id), "rawId" to b64(key.id), "type" to "public-key",
                "authenticatorAttachment" to "platform",
                "response" to mapOf(
                    "clientDataJSON" to b64(clientData),
                    "authenticatorData" to b64(authData),
                    "signature" to b64(signature),
                    "userHandle" to b64(key.userHandle),
                ),
                "clientExtensionResults" to emptyMap<String, Any>(),
            )
        )
    }

    private fun authData(withCredential: Key?, counter: Int): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(sha256(rpId.toByteArray()))
        // UP, UV, backup eligible + backed up (synced like Bitwarden), AT when a credential follows
        var flags = 0x01 or 0x04 or 0x08 or 0x10
        if (withCredential != null) flags = flags or 0x40
        out.write(flags)
        out.write(ByteBuffer.allocate(4).putInt(counter).array())
        if (withCredential != null) {
            out.write(aaguid)
            out.write(ByteBuffer.allocate(2).putShort(withCredential.id.size.toShort()).array())
            out.write(withCredential.id)
            val point = (withCredential.keyPair.public as ECPublicKey).w
            out.write(Cbor.encode(mapOf(1 to 2, 3 to -7, -1 to 1, -2 to point.affineX.unsigned32(), -3 to point.affineY.unsigned32())))
        }
        return out.toByteArray()
    }

    private fun json(value: Map<String, Any?>): String = mapAdapter.toJson(value)

    companion object {
        /** AAGUID of the Bitwarden passkey provider */
        val BITWARDEN_AAGUID = byteArrayOf(
            0xd5.toByte(), 0x48, 0x82.toByte(), 0x6e, 0x79, 0xb4.toByte(), 0xdb.toByte(), 0x40,
            0xa3.toByte(), 0xd8.toByte(), 0x11, 0x11, 0x6f, 0x7e, 0x83.toByte(), 0x49,
        )

        private fun sha256(data: ByteArray) = MessageDigest.getInstance("SHA-256").digest(data)
        private fun b64(data: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(data)
        private fun b64decode(text: String) = Base64.getUrlDecoder().decode(text)

        private fun BigInteger.unsigned32(): ByteArray {
            val bytes = toByteArray().let { if (it.size > 32) it.copyOfRange(it.size - 32, it.size) else it }
            return ByteArray(32 - bytes.size) + bytes
        }
    }
}

/**
 * The CBOR subset WebAuthn needs: maps, byte and text strings, small integers
 */
private object Cbor {
    fun encode(value: Any?): ByteArray = ByteArrayOutputStream().also { write(it, value) }.toByteArray()

    private fun write(out: ByteArrayOutputStream, value: Any?) {
        when (value) {
            is Int -> if (value >= 0) head(out, 0, value.toLong()) else head(out, 1, -1L - value)
            is ByteArray -> {
                head(out, 2, value.size.toLong())
                out.write(value)
            }

            is String -> {
                val bytes = value.toByteArray()
                head(out, 3, bytes.size.toLong())
                out.write(bytes)
            }

            is Map<*, *> -> {
                head(out, 5, value.size.toLong())
                value.forEach { (k, v) ->
                    write(out, k)
                    write(out, v)
                }
            }

            else -> error("unsupported CBOR value $value")
        }
    }

    private fun head(out: ByteArrayOutputStream, major: Int, length: Long) {
        val type = major shl 5
        when {
            length < 24 -> out.write(type or length.toInt())
            length < 0x100 -> {
                out.write(type or 24)
                out.write(length.toInt())
            }

            else -> {
                out.write(type or 25)
                out.write((length shr 8).toInt() and 0xff)
                out.write(length.toInt() and 0xff)
            }
        }
    }
}
