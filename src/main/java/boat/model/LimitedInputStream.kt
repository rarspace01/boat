package boat.model

import java.io.FilterInputStream
import java.io.InputStream

class LimitedInputStream(
    inputStream: InputStream,
    private var bytesRemaining: Long,
    ) : FilterInputStream(inputStream) {

        override fun read(): Int {
            if (bytesRemaining <= 0) {
                return -1
            }

            val byte = super.read()
            if (byte != -1) {
                bytesRemaining--
            }
            return byte
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (bytesRemaining <= 0) {
                return -1
            }

            val bytesToRead = minOf(length.toLong(), bytesRemaining).toInt()
            val bytesRead = super.read(buffer, offset, bytesToRead)
            if (bytesRead > 0) {
                bytesRemaining -= bytesRead
            }
            return bytesRead
        }
    }