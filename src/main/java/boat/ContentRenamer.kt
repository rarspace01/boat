package boat

import boat.info.MediaProxyService
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class ContentRenamer(val mediaProxyService: MediaProxyService) {

    companion object {
        val seasonPattern = "S([0-9]{1,2})(E[0-9]{1,2})?".toRegex(RegexOption.IGNORE_CASE)
        val seasonPattern2 = "(?:\\[|\\b)([0-9]{1,2})x([0-9]{1,2})(?:\\]|\\b)".toRegex()
        val episodePattern =
            "(?<=^|[^a-zA-Z0-9])(?:E|Ep|\\s-\\s|\\s|-|\\.)([0-9]{1,3})(?=[^a-zA-Z0-9]|$)".toRegex(RegexOption.IGNORE_CASE)
        val standaloneEpisodePattern = "(?:\\s|^)([0-9]{3})(?:\\s|$)".toRegex()
        val yearPattern = "\\b([1-2][0-9]{3})\\b".toRegex()
        val bracketPattern = "\\[.*?]".toRegex()
        val dvdPattern = "(?<=^|\\s)(?:dvd|disc|cd)\\s?([0-9]{1,2})".toRegex(RegexOption.IGNORE_CASE)
        val seasonInNamePattern = "(?<=^|\\s)Season\\s([0-9]{1,2})".toRegex(RegexOption.IGNORE_CASE)
        val startsWithEpisodeNumberPattern = "^[0-9]{1,3}\\s?[.x-]\\s?[0-9]{1,3}\\s?-\\s?".toRegex()
    }

    fun cleanName(inputName: String): String {
        var name = inputName
        // Remove common release group/site prefixes
        val sitePrefixPattern = "^(www\\..*?\\.(?:com|org|net|me|to|tv|re|io)\\s-\\s)".toRegex(RegexOption.IGNORE_CASE)
        name = name.replace(sitePrefixPattern, "")

        val groupPrefixPattern = "^([a-z0-9]{4,10}-)".toRegex(RegexOption.IGNORE_CASE)
        // check if it's a known garbage prefix (like 4 hex chars or similar)
        val match = groupPrefixPattern.find(name)
        if (match != null && !name.lowercase().startsWith("star") &&
            !name.lowercase().matches("^[0-9]+-(?:lb|lb\\.|pound|foot|inch|mile|meter|gram|ton|year|day|hour|min|sec|part|way|ways).*".toRegex())
        ) {
            name = name.replace(groupPrefixPattern, "")
        }

        name = name.replace("(", "").replace(")", "").replace("_", " ").trim()
        name = name.removePrefix(".")

        val seasonMatch = seasonPattern.find(name)
        val seasonMatch2 = seasonPattern2.find(name)
        val seasonMatch3 = seasonInNamePattern.find(name)
        val dvdMatch = dvdPattern.find(name)
        if (seasonMatch != null) {
            name = name.substring(0, seasonMatch.range.first)
        } else if (seasonMatch2 != null) {
            name = name.substring(0, seasonMatch2.range.first)
        } else if (seasonMatch3 != null) {
            name = name.substring(0, seasonMatch3.range.first)
        } else if (dvdMatch != null) {
            name = name.substring(0, dvdMatch.range.first)
        } else {
            // Special handling for episode numbers that might be separated by a dash without spaces
            val nameForEpisodeMatching = name.replace("-", " ")
            val episodeMatch = episodePattern.find(nameForEpisodeMatching)
            if (episodeMatch != null) {
                name = name.substring(0, episodeMatch.range.first)
            } else {
                val standaloneEpisodeMatch = standaloneEpisodePattern.find(name)
                if (standaloneEpisodeMatch != null && yearPattern.find(standaloneEpisodeMatch.value) == null) {
                    name = name.substring(0, standaloneEpisodeMatch.range.first)
                } else {
                    val yearMatch = yearPattern.find(name)
                    if (yearMatch != null) {
                        name = name.substring(0, yearMatch.range.first)
                    }
                }
            }
        }

        name = name.replace(bracketPattern, "").trim()
        name = name.replace(".", " ").replace("_", " ").trim()

        // Remove trailing dashes or other common separators
        while (name.endsWith("-") || name.endsWith(".")) {
            name = name.substring(0, name.length - 1).trim()
        }

        // If extracted name ends with a number that looks like an episode (e.g., show-24)
        // and we haven't truncated it yet, do a last resort truncation
        val trailingEpisodePattern = "-([0-9]{1,3})$".toRegex()
        val trailingMatch = trailingEpisodePattern.find(name)
        if (trailingMatch != null) {
            name = name.substring(0, trailingMatch.range.first).trim()
        }
        return name
    }

    fun isJunk(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return true
        if (trimmed.length <= 1 || trimmed.all { !it.isLetterOrDigit() }) return true

        val normalized = trimmed.lowercase().replace("_", " ").replace(".", " ").replace("-", " ").trim()
        val lower = trimmed.lowercase()

        // Category / root folder names
        val categoryFolderNames = setOf(
            "series-shows", "series shows", "series", "shows", "tv", "tv shows", "tv show", "tv-shows",
            "tv series", "tv-series", "movies", "movie", "films", "film", "anime",
            "cartoons", "cartoon", "documentaries", "documentary", "downloads", "download",
            "torrents", "torrent", "complete", "incomplete", "pfdb", "data", "videos",
            "video", "media", "home", "root", "desktop"
        )
        if (categoryFolderNames.contains(lower) || categoryFolderNames.contains(normalized)) return true

        // Range folder names like "0-9", "a-z", etc.
        if (lower.matches("^[0-9]-[0-9]$".toRegex()) || lower.matches("^[a-z]-[a-z]$".toRegex())) return true

        // Magnet URI or URLs
        if (lower.startsWith("magnet:") || lower.contains("xt=urn:") || lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("www.")) return true
        if (lower.endsWith(".com") || lower.endsWith(".org") || lower.endsWith(".net") || lower.endsWith(".me") || lower.endsWith(".to") || lower.endsWith(".tv") || lower.endsWith(".io")) return true

        // Pure numbers
        if (lower.matches("^[0-9]+$".toRegex())) return true

        // Pure hex hashes (e.g. 1E372EE5, BB605406)
        if (lower.matches("^[0-9a-f]{8,}$".toRegex()) && lower.any { it.isDigit() } && lower.any { it.isLetter() }) return true

        // Season/Episode/Part/Volume/Disc patterns
        if (normalized.matches("^(?:season|series|disc|disk|cd|dvd|bd|volume|vol|book|chapter|part|ep|episode)\\s*[0-9]+.*$".toRegex())) return true
        if (normalized.matches("^s[0-9]{1,2}(?:\\s?e[0-9]{1,2})?$".toRegex())) return true
        if (normalized.matches("^[0-9]{1,2}\\s?x\\s?[0-9]{1,2}$".toRegex())) return true
        if (normalized.matches("^[0-9]{1,3}\\s?[.\\sx-]\\s?[0-9]{1,3}$".toRegex())) return true
        if (normalized.matches("^[0-9]+\\s?[a-z]{2,3}$".toRegex())) return true // e.g. "6 spa", "6_spa"

        // Auxiliary exact keywords
        val auxiliaryExactKeywords = setOf(
            "subs", "sub", "subtitles", "subtitle", "vobsub", "idx", "srt",
            "extras", "extra", "featurettes", "featurette", "bonus", "bonuses",
            "sample", "samples", "proof", "proofs", "screenshot", "screenshots",
            "trailer", "trailers", "teaser", "teasers", "promo", "promos", "promotional",
            "deleted scenes", "deleted scene", "deleted", "deletedscenes",
            "outtakes", "outtake", "bloopers", "blooper", "gag reel", "gag reels", "gagreel", "gagreels",
            "behind the scenes", "behindthescenes", "making of", "makingof",
            "screentests", "screentest", "interview", "interviews", "cast", "crew",
            "character profile", "character profiles", "profiles", "profile",
            "original scripts", "script", "scripts",
            "covers", "cover", "artwork", "artworks", "art", "fonts", "font", "scans", "scan", "covers and fonts",
            "soundtrack", "soundtracks", "ost", "music video", "music videos", "music", "audio",
            "metadata", "nfo", "info", "other", "misc", "miscellaneous",
            "special", "specials", "sp", "ova", "ona",
            "textless", "textless opening", "textless ending", "ncop", "nced", "creditless",
            "episode guide", "guide", "cfwm", "teneighty", "greatest saga"
        )
        if (auxiliaryExactKeywords.contains(lower) || auxiliaryExactKeywords.contains(normalized)) return true

        // Technical / codec tags when standalone
        val technicalTags = setOf(
            "1080p", "720p", "2160p", "480p", "576p", "4k", "uhd", "fhd", "hdtv",
            "bluray", "blu-ray", "bdrip", "brrip", "dvdrip", "webrip", "web-dl", "webdl", "web", "remux", "proper", "repack", "rerip",
            "x264", "x265", "h264", "h265", "hevc", "avc", "xvid", "divx", "aac", "ac3", "dts", "ddp5 1", "ddp5.1", "10bit", "8bit", "hdr",
            "rarbg", "ettv", "eztv", "rartv", "tgx", "yts", "yify", "megusta"
        )
        if (technicalTags.contains(lower) || technicalTags.contains(normalized)) return true

        val junkPrefixes = listOf(
            "season ", "seasons ", "disc ", "disc", "disk ", "disk", "cd ", "cd", "dvd ", "dvd",
            "volume ", "vol ", "book ", "chapter ", "part ", "ep ", "episode ", "subs ", "subtitles ",
            "extras ", "featurette ", "featurettes ", "trailer ", "trailers ", "sample ", "samples ",
            "screenshot ", "screenshots ", "deleted scene", "deleted scenes", "making of ", "behind the scenes "
        )
        if (junkPrefixes.any { normalized.startsWith(it) || lower.startsWith(it) }) return true

        val allTechnicalAndJunkTokens = categoryFolderNames + auxiliaryExactKeywords + technicalTags + setOf(
            "1080p", "720p", "2160p", "480p", "576p", "4k", "uhd", "fhd", "hd", "sd", "hdtv", "pdtv",
            "bluray", "blu", "ray", "bdrip", "brrip", "dvdrip", "dvd", "webrip", "web", "dl", "webdl",
            "remux", "proper", "repack", "rerip", "real", "extended", "unrated",
            "x264", "x265", "h264", "h265", "hevc", "avc", "xvid", "divx", "vp9", "av1",
            "aac", "ac3", "eac3", "dts", "ddp", "ddp5", "ddp51", "ddp5 1", "flac", "mp3", "truehd", "atmos",
            "10bit", "8bit", "hdr", "hdr10", "hdr10plus", "dv", "dovi", "sdr",
            "rarbg", "ettv", "eztv", "rartv", "tgx", "yts", "yify", "megusta", "vxt", "galaxytv",
            "complete", "season", "seasons", "series", "ep", "episode", "episodes", "disc", "disk", "cd", "vol", "volume"
        )

        val tokens = normalized.split(" ").filter { it.isNotBlank() }
        if (tokens.isNotEmpty() && tokens.all { token ->
            token.length <= 1 || token.matches("^[0-9]+$".toRegex()) || allTechnicalAndJunkTokens.contains(token)
        }) {
            return true
        }

        return false
    }

    private fun isAuxiliaryPathOrFile(file: File, cleanedFileName: String): Boolean {
        val nonMediaExtensions = setOf(
            "pdf", "txt", "nfo", "jpg", "jpeg", "png", "gif", "bmp", "doc", "docx",
            "xls", "xlsx", "exe", "zip", "rar", "7z", "tar", "gz", "sfv", "par2", "cue", "md5", "torrent", "url"
        )
        if (nonMediaExtensions.contains(file.extension.lowercase())) return true

        val auxiliaryFolderNames = setOf(
            "extras", "featurettes", "bonus", "samples", "screenshots", "trailers",
            "deleted scenes", "screentests", "subs", "subtitles", "original scripts",
            "soundtracks", "soundtrack", "ost", "artwork", "covers", "fonts", "scans", "proofs", "specials"
        )

        var p = file.parentFile
        while (p != null) {
            val pName = decodeIfEncoded(p.name).lowercase().trim()
            if (auxiliaryFolderNames.contains(pName) || pName.startsWith("extras") || pName.startsWith("featurette") || pName.startsWith("trailer")) {
                return true
            }
            p = p.parentFile
        }

        val lowerCleaned = cleanedFileName.lowercase()
        val auxiliaryFileKeywords = listOf(
            "featurette", "trailer", "deleted scene", "screentest", "outtake",
            "blooper", "gag reel", "screenshot", "sample", "character profile",
            "behind the scenes", "making of", "interview", "original script", "textless"
        )
        if (auxiliaryFileKeywords.any { lowerCleaned.contains(it) }) {
            val fileNameWithoutExt = file.nameWithoutExtension
            if (seasonPattern.find(fileNameWithoutExt) == null && seasonPattern2.find(fileNameWithoutExt) == null) {
                return true
            }
        }

        return false
    }

    fun extractName(file: File): String {
        val fileName = decodeIfEncoded(file.name)
        val rawBaseName = fileName.substringBeforeLast(".")
        val cleanedFileName = cleanName(rawBaseName)

        val isCandidate0Junk = isJunk(cleanedFileName) ||
                startsWithEpisodeNumberPattern.find(rawBaseName) != null ||
                rawBaseName.lowercase().startsWith("s0") ||
                rawBaseName.lowercase().startsWith("s1") ||
                rawBaseName.lowercase().startsWith("s2") ||
                rawBaseName.lowercase().startsWith("s3") ||
                isAuxiliaryPathOrFile(file, cleanedFileName)

        if (!isCandidate0Junk && cleanedFileName.isNotBlank()) {
            return cleanedFileName
        }

        // If filename is junk or auxiliary, search upwards in parent directory hierarchy
        var currentFile: File? = file.parentFile
        while (currentFile != null) {
            val parentName = decodeIfEncoded(currentFile.name)
            val cleanedParentName = cleanName(parentName)
            if (cleanedParentName.isNotBlank() && !isJunk(cleanedParentName)) {
                return cleanedParentName
            }
            currentFile = currentFile.parentFile
        }

        return ""
    }

    fun extractYear(fullName: String): Int? {
        val yearPattern = "\\b([1-2][0-9]{3})\\b".toRegex()
        val match = yearPattern.find(File(fullName).name) ?: yearPattern.find(File(fullName).parentFile?.name ?: "")
        return match?.groupValues?.get(1)?.toInt()
    }

    fun extractSeason(file: File): Int? {
        val fileName = file.name
        val parentName = file.parentFile?.name ?: ""
        val grandParentName = file.parentFile?.parentFile?.name ?: ""
        val greatGrandParentName = file.parentFile?.parentFile?.parentFile?.name ?: ""

        val seasonPattern = "S([0-9]{1,2})".toRegex(RegexOption.IGNORE_CASE)
        val seasonPatternInParent = "(?<=Season\\s)([0-9]{1,2})".toRegex(RegexOption.IGNORE_CASE)
        val seasonPattern2 = "(?:\\[|\\b)([0-9]{1,2})x([0-9]{1,2})(?:\\]|\\b)".toRegex()
        val dvdPattern = "(?<=^|\\s)(?:dvd|disc|cd)\\s?([0-9]{1,2})".toRegex(RegexOption.IGNORE_CASE)

        val match = seasonPattern.find(fileName)
            ?: seasonPattern.find(parentName)
            ?: seasonPatternInParent.find(parentName)
            ?: seasonPattern.find(grandParentName)
            ?: seasonPattern.find(greatGrandParentName)
            ?: dvdPattern.find(fileName)

        if (match != null) {
            return match.groupValues[match.groupValues.size - 1].toInt()
        }

        val match2 = seasonPattern2.find(fileName)
            ?: seasonPattern2.find(parentName)
            ?: seasonPattern2.find(grandParentName)
            ?: seasonPattern2.find(greatGrandParentName)

        return match2?.groupValues?.get(1)?.toInt()
    }

    fun extractEpisode(file: File): Int? {
        val fileName = file.name
        val parentName = file.parentFile?.name ?: ""
        val grandParentName = file.parentFile?.parentFile?.name ?: ""
        val greatGrandParentName = file.parentFile?.parentFile?.parentFile?.name ?: ""

        val episodePattern =
            "(?<=^|[^a-zA-Z0-9])(?:E|Ep|\\s-\\s|\\s|-|\\.)([0-9]{1,3})(?=[^a-zA-Z0-9]|$)".toRegex(RegexOption.IGNORE_CASE)
        val standaloneEpisodePattern = "(?:\\s|^)([0-9]{3})(?:\\s|$)".toRegex()
        val yearPattern = "\\b([1-2][0-9]{3})\\b".toRegex()
        val seasonEpisodePattern = "S[0-9]{1,2}E([0-9]{1,2})".toRegex(RegexOption.IGNORE_CASE)
        val seasonPattern2 = "(?:\\[|\\b)([0-9]{1,2})x([0-9]{1,2})(?:\\]|\\b)".toRegex()
        val replaced = fileName.replace("_", " ")

        val match2 = seasonPattern2.find(fileName)
            ?: seasonPattern2.find(parentName)
            ?: seasonPattern2.find(grandParentName)
            ?: seasonPattern2.find(greatGrandParentName)

        if (match2 != null) {
            return match2.groupValues[2].toInt()
        }

        val match = seasonEpisodePattern.find(fileName)
            ?: seasonEpisodePattern.find(parentName)
            ?: seasonEpisodePattern.find(grandParentName)
            ?: seasonEpisodePattern.find(greatGrandParentName)
            ?: episodePattern.find(replaced)
            ?: episodePattern.find(fileName)
            ?: standaloneEpisodePattern.find(fileName)?.let { if (yearPattern.find(it.value) == null) it else null }
            ?: "-([0-9]{1,3})\\.".toRegex().find(fileName)
            ?: episodePattern.find(parentName)
            ?: episodePattern.find(grandParentName)

        if (match != null) {
            return match.groupValues[1].toInt()
        }

        return null
    }

    fun processFilesFromDirectory(path: String, logFile: File? = null) {
        val files = File(path).listFiles()
        files?.let { files ->
            for (file in files) {
                if (file.isFile) {
                    val fullName = file.absolutePath
                    // extract Movie or TV show name from full path
                    val name = extractName(file)
                    val year = extractYear(file.absolutePath)
                    var season = extractSeason(file)
                    val episode = extractEpisode(file)

                    val queriedName = if (name.isNotBlank()) {
                        if (year != null) {
                            mediaProxyService.search(name, year).firstOrNull()
                        } else {
                            mediaProxyService.search(name).firstOrNull()
                        }
                    } else {
                        null
                    }

                    if (season == null && episode != null && queriedName != null && queriedName.seasonCount == 1) {
                        season = 1
                    }

                    val idString = queriedName?.id ?: ""
                    val logLine =
                        "F: $fullName -> Extracted: $name -> $queriedName (Y: $year, S: $season, E: $episode) $idString"
                    println(logLine)
                    logFile?.appendText(logLine + "\n")
                } else {
                    processFilesFromDirectory(file.absolutePath, logFile)
                }
            }
        }
    }

    fun decodeIfEncoded(name: String): String {
        return try {
            if (name.contains("%")) {
                URLDecoder.decode(name, StandardCharsets.UTF_8.toString())
            } else {
                name
            }
        } catch (_: Exception) {
            name
        }
    }
}