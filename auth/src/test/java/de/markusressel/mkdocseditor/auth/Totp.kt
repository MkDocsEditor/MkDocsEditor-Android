package de.markusressel.mkdocseditor.auth

import java.net.URI
import java.net.URLDecoder
import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * RFC 6238 codes (SHA1, 6 digits, 30 s) for live tests, like an authenticator app
 */
object Totp {

    fun step(nowMillis: Long = System.currentTimeMillis()): Long = nowMillis / 1000 / 30

    fun code(base32Secret: String, step: Long): String {
        val mac = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(base32Decode(base32Secret), "HmacSHA1")) }
        val hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array())
        val offset = hash.last().toInt() and 0x0f
        val binary = ByteBuffer.wrap(hash, offset, 4).int and 0x7fffffff
        return (binary % 1_000_000).toString().padStart(6, '0')
    }

    /** the secret parameter of an otpauth:// URI */
    fun secretOf(otpauthUri: String): String = URI(otpauthUri).rawQuery.split("&")
        .map { it.split("=", limit = 2) }
        .first { it[0] == "secret" }[1]
        .let { URLDecoder.decode(it, Charsets.UTF_8) }

    private fun base32Decode(text: String): ByteArray {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        var buffer = 0
        var bits = 0
        val out = java.io.ByteArrayOutputStream()
        text.uppercase().filter { it != '=' && it != ' ' }.forEach { c ->
            buffer = (buffer shl 5) or alphabet.indexOf(c).also { require(it >= 0) { "not base32" } }
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out.write((buffer shr bits) and 0xff)
            }
        }
        return out.toByteArray()
    }
}
