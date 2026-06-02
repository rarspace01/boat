package boat.model

data class ByteRange(
        val start: Long,
        val endInclusive: Long,
    ) {
        val length: Long = endInclusive - start + 1
    }