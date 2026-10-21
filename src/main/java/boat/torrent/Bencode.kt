package boat.torrent

/**
 * Minimal bencode reader for tracker answers. Strings are kept as bytes, the hashes in a scrape answer are binary and
 * must not go through a text decoding. Dictionary keys are returned as ISO-8859-1 strings, so every byte stays one char.
 */
object Bencode {

    /** Long, ByteArray, List or Map<String, Any?>, null if [data] is not valid bencode. */
    fun decode(data: ByteArray): Any? = try {
        val reader = Reader(data)
        reader.read().takeIf { reader.position == data.size }
    } catch (_: RuntimeException) {
        null
    }

    private class Reader(private val data: ByteArray) {
        var position = 0

        fun read(): Any? = when (data[position].toInt().toChar()) {
            'i' -> readInteger()
            'l' -> readList()
            'd' -> readDictionary()
            in '0'..'9' -> readBytes()
            else -> throw IllegalArgumentException("unexpected byte at $position")
        }

        private fun readInteger(): Long {
            val end = indexOf('e')
            val number = String(data, position + 1, end - position - 1, Charsets.ISO_8859_1).toLong()
            position = end + 1
            return number
        }

        private fun readBytes(): ByteArray {
            val colon = indexOf(':')
            val length = String(data, position, colon - position, Charsets.ISO_8859_1).toInt()
            require(length >= 0 && colon + 1 + length <= data.size) { "string out of range" }
            val bytes = data.copyOfRange(colon + 1, colon + 1 + length)
            position = colon + 1 + length
            return bytes
        }

        private fun readList(): List<Any?> {
            position++
            val list = mutableListOf<Any?>()
            while (data[position].toInt().toChar() != 'e') list.add(read())
            position++
            return list
        }

        private fun readDictionary(): Map<String, Any?> {
            position++
            val map = linkedMapOf<String, Any?>()
            while (data[position].toInt().toChar() != 'e') {
                val key = String(readBytes(), Charsets.ISO_8859_1)
                map[key] = read()
            }
            position++
            return map
        }

        private fun indexOf(character: Char): Int {
            var index = position
            while (index < data.size && data[index].toInt().toChar() != character) index++
            require(index < data.size) { "missing $character" }
            return index
        }
    }
}
