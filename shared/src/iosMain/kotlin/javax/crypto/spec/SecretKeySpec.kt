package javax.crypto.spec

class SecretKeySpec(val encoded: ByteArray, val algorithm: String) {
    fun getEncoded(): ByteArray = encoded
}
