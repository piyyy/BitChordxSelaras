package java.io

typealias IOException = kotlinx.io.IOException
typealias EOFException = kotlinx.io.EOFException
typealias Closeable = AutoCloseable

class UncheckedIOException(message: String?, cause: Throwable?) : RuntimeException(message, cause)
class FileNotFoundException(message: String? = null) : kotlinx.io.IOException(message)
class InterruptedIOException(message: String? = null) : kotlinx.io.IOException(message)
