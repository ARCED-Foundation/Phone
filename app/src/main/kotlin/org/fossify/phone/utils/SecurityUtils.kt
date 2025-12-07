package org.fossify.phone.utils

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.experimental.and

object SecurityUtils {
    private const val ITERATIONS = 12000
    private const val KEY_LENGTH = 256
    private const val DELIMITER = ":"

    private val secureRandom = SecureRandom()

    fun hashSecret(secret: String): String {
        val salt = ByteArray(16).also { secureRandom.nextBytes(it) }
        val hash = pbkdf2(secret, salt)
        return listOf(ITERATIONS.toString(), salt.toBase64(), hash.toBase64()).joinToString(DELIMITER)
    }

    fun verifySecret(secret: String, stored: String): Boolean {
        val parts = stored.split(DELIMITER)
        if (parts.size != 3) return false

        val iterations = parts[0].toIntOrNull() ?: return false
        val salt = parts[1].fromBase64() ?: return false
        val expected = parts[2].fromBase64() ?: return false
        val actual = pbkdf2(secret, salt, iterations)
        return constantTimeEquals(expected, actual)
    }

    private fun pbkdf2(secret: String, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray {
        val spec = PBEKeySpec(secret.toCharArray(), salt, iterations, KEY_LENGTH)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i] xor b[i])
        }
        return result == 0
    }

    private infix fun Byte.xor(other: Byte): Int = (this.toInt() xor other.toInt())

    private fun ByteArray.toBase64(): String = android.util.Base64.encodeToString(this, android.util.Base64.NO_WRAP)

    private fun String.fromBase64(): ByteArray? =
        try {
            android.util.Base64.decode(this, android.util.Base64.NO_WRAP)
        } catch (e: IllegalArgumentException) {
            null
        }
}
