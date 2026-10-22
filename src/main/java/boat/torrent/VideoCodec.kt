package boat.torrent

import java.util.Locale

/**
 * Video codecs ordered by compression efficiency, the most efficient first. A more efficient codec gets the same
 * quality into a smaller file, which the size part of the rating would otherwise penalize, so it adds [rating].
 */
enum class VideoCodec(val rating: Double, tags: List<String>) {
    // few players decode it yet
    VVC(0.6, listOf("vvc", "h266", "x266")),
    AV1(0.5, listOf("av1")),
    HEVC(0.4, listOf("hevc", "h265", "x265")),
    VP9(0.35, listOf("vp9")),
    H264(0.2, listOf("avc", "h264", "x264")),
    VC1(0.15, listOf("vc1")),
    MPEG4_PART2(0.1, listOf("xvid", "divx")),
    // full dvd images are always mpeg-2
    MPEG2(0.0, listOf("mpeg2", "dvdr", "dvd5", "dvd9")),
    MPEG1(-0.1, listOf("mpeg1", "vcd"));

    // tags stand alone in a name, "h.265", "H 265", "vc-1" and "mpeg-2" are written with a separator
    private val pattern = tags.joinToString("|", "(?<![a-z0-9])(?:", ")(?![a-z0-9])") { tag ->
        Regex("^([a-z]+)(\\d+)$").matchEntire(tag)?.let { "${it.groupValues[1]}[ .-]?${it.groupValues[2]}" } ?: tag
    }.toRegex()

    companion object {
        /**
         * Names without a codec tag are mostly H.264, the most common codec.
         */
        val DEFAULT = H264

        /**
         * The most efficient codec named in [torrentName], null if it names none.
         */
        fun of(torrentName: String): VideoCodec? {
            val lowerCaseName = torrentName.lowercase(Locale.ROOT)
            return entries.firstOrNull { it.pattern.containsMatchIn(lowerCaseName) }
        }
    }
}
