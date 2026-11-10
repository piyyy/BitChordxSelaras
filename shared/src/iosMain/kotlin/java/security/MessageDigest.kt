@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package java.security

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.reinterpret
import platform.CoreCrypto.CC_MD5
import platform.CoreCrypto.CC_MD5_DIGEST_LENGTH
import platform.CoreCrypto.CC_SHA1
import platform.CoreCrypto.CC_SHA1_DIGEST_LENGTH
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH

class NoSuchAlgorithmException(message: String) : Exception(message)

/** CommonCrypto behind java.security.MessageDigest; buffers updates and hashes on digest(). */
class MessageDigest private constructor(private val algorithm: String) {
    private val buffer = ArrayList<Byte>()

    fun update(bytes: ByteArray) { bytes.forEach { buffer.add(it) } }
    fun update(byte: Byte) { buffer.add(byte) }
    fun reset() = buffer.clear()
    fun digest(bytes: ByteArray): ByteArray { update(bytes); return digest() }

    fun digest(): ByteArray {
        val input = buffer.toByteArray()
        buffer.clear()
        val size = when (algorithm) {
            "MD5" -> CC_MD5_DIGEST_LENGTH
            "SHA-1", "SHA1" -> CC_SHA1_DIGEST_LENGTH
            else -> CC_SHA256_DIGEST_LENGTH
        }
        val out = ByteArray(size)
        val source = if (input.isEmpty()) ByteArray(1) else input
        source.usePinned { src ->
            out.usePinned { dst ->
                when (algorithm) {
                    "MD5" -> CC_MD5(src.addressOf(0), input.size.convert(), dst.addressOf(0).reinterpretU())
                    "SHA-1", "SHA1" -> CC_SHA1(src.addressOf(0), input.size.convert(), dst.addressOf(0).reinterpretU())
                    else -> CC_SHA256(src.addressOf(0), input.size.convert(), dst.addressOf(0).reinterpretU())
                }
            }
        }
        return out
    }

    companion object {
        fun getInstance(algorithm: String): MessageDigest {
            val name = algorithm.uppercase()
            if (name !in setOf("MD5", "SHA-1", "SHA1", "SHA-256", "SHA256")) throw NoSuchAlgorithmException(algorithm)
            return MessageDigest(if (name == "SHA256") "SHA-256" else name)
        }
    }
}

internal fun kotlinx.cinterop.CPointer<kotlinx.cinterop.ByteVar>.reinterpretU(): kotlinx.cinterop.CPointer<kotlinx.cinterop.UByteVar> =
    reinterpret<kotlinx.cinterop.UByteVar>()
