@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package javax.crypto

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CCHmac
import platform.CoreCrypto.CC_SHA1_DIGEST_LENGTH
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreCrypto.kCCHmacAlgSHA1
import platform.CoreCrypto.kCCHmacAlgSHA256

/** HMAC-SHA1/SHA256 over CommonCrypto, as javax.crypto.Mac. */
class Mac private constructor(private val algorithm: String) {
    private var key = ByteArray(0)
    private val data = ArrayList<Byte>()

    fun init(spec: javax.crypto.spec.SecretKeySpec) { key = spec.encoded; data.clear() }
    fun update(bytes: ByteArray) { bytes.forEach { data.add(it) } }
    fun doFinal(bytes: ByteArray): ByteArray { update(bytes); return doFinal() }

    fun doFinal(): ByteArray {
        val sha1 = algorithm.contains("SHA1", ignoreCase = true)
        val out = ByteArray(if (sha1) CC_SHA1_DIGEST_LENGTH else CC_SHA256_DIGEST_LENGTH)
        val input = data.toByteArray().let { if (it.isEmpty()) ByteArray(1) else it }
        val length = data.size
        val k = if (key.isEmpty()) ByteArray(1) else key
        k.usePinned { kp ->
            input.usePinned { ip ->
                out.usePinned { op ->
                    CCHmac(
                        (if (sha1) kCCHmacAlgSHA1 else kCCHmacAlgSHA256).convert(),
                        kp.addressOf(0), key.size.convert(), ip.addressOf(0), length.convert(), op.addressOf(0),
                    )
                }
            }
        }
        data.clear()
        return out
    }

    companion object {
        fun getInstance(algorithm: String) = Mac(algorithm)
    }
}
