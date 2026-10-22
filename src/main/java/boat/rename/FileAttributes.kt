package boat.rename

import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Facts about a media file taken from its name and folders: resolution, codec, subtitle language, extras.
 */
object FileAttributes {
    val letterOSeasonPattern = "(?<=^|[^a-zA-Z0-9])[Ss][Oo](?=[0-9][Ee][0-9])".toRegex()

    // any height like 360p, 405p, 1080i or 1440p, also with a frame rate like "hd720p30"
    val resolutionPattern = "(?i)(?<![a-z0-9])(?:hd)?([0-9]{3,4})([pi])(?:[0-9]{2})?(?![a-z0-9])".toRegex()

    val kResolutionPattern = "(?i)(?<![a-z0-9])([248]k)(?![a-z0-9])".toRegex()

    // width and height like 1920x1080 or 1280x960
    val dimensionsPattern = "(?<![0-9])([0-9]{3,4})x([0-9]{3,4})(?![0-9])".toRegex()

    private val resolutionHeights = 240..4320

    val codecPattern = "(?i)(?<=^|[^a-zA-Z0-9])(x265|x264|h265|h264|hevc|avc|xvid|divx|av1|vp9|h\\.264|h\\.265|x\\.264|x\\.265)(?=[^a-zA-Z0-9]|$)".toRegex()

    val movieExtraPattern = "(?i)(?<=^|[^a-zA-Z0-9])(?:sample|trailer|trailers|featurette|featurettes|extra|extras|bonus|behind[\\s_.-]?the[\\s_.-]?scenes|deleted[\\s_.-]scenes?|interviews?)(?=[^a-zA-Z0-9]|$)".toRegex()

    val subtitleExtensions = setOf("srt", "sub", "idx", "ass", "ssa", "vtt", "sup")

    val videoExtensions = setOf("mkv", "mp4", "avi", "m4v", "mov", "wmv", "mpg", "mpeg", "ts", "m2ts", "webm", "flv", "divx", "ogm")

    private val languageCodes = java.util.Locale.getISOLanguages().flatMap { listOfNotNull(it, runCatching { java.util.Locale.of(it).isO3Language }.getOrNull()) }.toSet() +
            setOf("alb", "arm", "baq", "bur", "chi", "cze", "dut", "fre", "geo", "ger", "gre", "ice", "mac", "mao", "may", "per", "rum", "slo", "tib", "wel")

    /**
     * Files worth renaming for a movie, everything else (nfo, txt, images, ...) is left untouched.
     */
    fun isMovieFile(file: File): Boolean {
        val extension = file.extension.lowercase()
        return extension in videoExtensions || extension in subtitleExtensions
    }

    fun extractResolution(file: File): String? {
        var current: File? = file
        var depth = 0
        while (current != null && depth < 4) {
            val name = decodeIfEncoded(current.name)
            resolutionPattern.findAll(name).firstOrNull { it.groupValues[1].toInt() in resolutionHeights }
                ?.let { return it.groupValues[1] + it.groupValues[2].lowercase() }
            kResolutionPattern.find(name)?.let { return it.groupValues[1].lowercase() }
            dimensionsPattern.findAll(name).firstOrNull { it.groupValues[2].toInt() in resolutionHeights && it.groupValues[1].toInt() > it.groupValues[2].toInt() }
                ?.let { return it.groupValues[2] + "p" }
            current = current.parentFile
            depth++
        }
        return null
    }

    fun extractCodec(file: File): String? {
        var current: File? = file
        var depth = 0
        while (current != null && depth < 4) {
            val name = decodeIfEncoded(current.name)
            val match = codecPattern.find(name)
            if (match != null) {
                return match.groupValues[1].lowercase().replace(".", "")
            }
            current = current.parentFile
            depth++
        }
        return null
    }

    fun isMovieExtra(file: File): Boolean {
        return movieExtraPattern.containsMatchIn(decodeIfEncoded(file.nameWithoutExtension)) ||
                file.parentFile?.let { movieExtraPattern.containsMatchIn(decodeIfEncoded(it.name)) } == true
    }

    fun extractSubtitleLanguage(file: File): String? {
        val parts = file.nameWithoutExtension.lowercase().split('.')
        // e.g. movie.en.srt, movie.eng.forced.srt
        val flags = setOf("forced", "sdh", "cc")
        val languageIndex = parts.indexOfLast { it !in flags }
        if (languageIndex <= 0) return null
        val language = parts[languageIndex]
        if (language.length !in 2..3 || language !in languageCodes) return null
        return (listOf(language) + parts.drop(languageIndex + 1)).joinToString(".")
    }

    fun decodeIfEncoded(name: String): String {
        val decodedName = try {
            if (name.contains("%")) {
                URLDecoder.decode(name, StandardCharsets.UTF_8.toString())
            } else {
                name
            }
        } catch (_: Exception) {
            name
        }
        // a letter O instead of the zero, e.g. "The.Blacklist.SO1E01"
        return decodedName.replace(letterOSeasonPattern, "S0").replace("&#039;", "'").replace("&amp;", "&")
    }
}
