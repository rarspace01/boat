package boat

import boat.info.MediaItem
import boat.info.MediaProxyService
import boat.info.MediaType
import boat.utilities.StringUtil
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class ContentRenamer(val mediaProxyService: MediaProxyService) {

    companion object {
        val seasonPattern = "(?<=^|[^a-zA-Z])S([0-9]{1,2})(E[0-9]{1,2})?".toRegex(RegexOption.IGNORE_CASE)
        val seasonPattern2 = "(?:\\[|\\b)([0-9]{1,2})x([0-9]{1,2})(?:\\]|\\b)".toRegex()
        val episodePattern =
            "(?:(?<=^|[^a-zA-Z0-9])(?:Episode|Ep\\.?|E)|(?:\\s-\\s|\\s|-|\\.))([0-9]{1,3})(?=[^a-zA-Z0-9]|$)".toRegex(RegexOption.IGNORE_CASE)
        val standaloneEpisodePattern = "(?:\\s|^)([0-9]{3})(?:\\s|$)".toRegex()
        val yearPattern = "\\b([1-2][0-9]{3})\\b".toRegex()
        val bracketPattern = "\\[.*?]".toRegex()
        val dvdPattern = "(?<=^|\\s)(?:dvd|disc|cd)\\s?([0-9]{1,2})".toRegex(RegexOption.IGNORE_CASE)
        val bookPattern = "(?<=^|\\s)(?:book|volume|vol|chapter)\\s?([0-9]{1,2})".toRegex(RegexOption.IGNORE_CASE)
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
        if (match != null && !name.lowercase().startsWith("star") && !name.lowercase().startsWith("hack") &&
            !name.lowercase().matches("^[0-9]+-(?:lb|lb\\.|pound|foot|inch|mile|meter|gram|ton|year|day|hour|min|sec|part|way|ways).*".toRegex())
        ) {
            name = name.replace(groupPrefixPattern, "")
        }

        name = name.replace("(", "").replace(")", "").replace("_", " ").trim()
        name = name.removePrefix(".")

        var truncated = false
        val seasonMatch = seasonPattern.find(name)
        val seasonMatch2 = seasonPattern2.find(name)
        val seasonMatch3 = seasonInNamePattern.find(name)
        val dvdMatch = dvdPattern.find(name)
        val bookMatch = bookPattern.find(name)
        if (seasonMatch != null) {
            name = name.substring(0, seasonMatch.range.first)
            truncated = true
        } else if (seasonMatch2 != null) {
            name = name.substring(0, seasonMatch2.range.first)
            truncated = true
        } else if (seasonMatch3 != null) {
            name = name.substring(0, seasonMatch3.range.first)
            truncated = true
        } else if (dvdMatch != null) {
            name = name.substring(0, dvdMatch.range.first)
            truncated = true
        } else if (bookMatch != null) {
            name = name.substring(0, bookMatch.range.first)
            truncated = true
        } else if (!name.matches("^[0-9]+(?:[-.][0-9]+)+$".toRegex())) {
            val episodeWithTitleMatch = "(?:\\s-\\s|\\s-_|_-_|\\s-\\s?|_-\\s?|\\s|^)(?:Episode|Ep\\.?|E)?\\s*([0-9]{1,3})\\s+-\\s+[a-zA-Z]".toRegex(RegexOption.IGNORE_CASE).find(name)
            val dashEpisodeMatch = "(?:\\s-\\s|\\s-_|_-_|\\s-\\s?|_-\\s?)([0-9]{1,3})(?=[^a-zA-Z0-9]|$)".toRegex().find(name)
            val explicitEpisodeMatch = "(?:\\s-\\s|\\s-_|_-_|\\s-\\s?|_-\\s?|\\s|^)(?:Episode|Ep\\.?|E)\\s*([0-9]{1,3})(?=[^a-zA-Z0-9]|$)".toRegex(RegexOption.IGNORE_CASE).find(name)
            if (episodeWithTitleMatch != null) {
                name = name.substring(0, episodeWithTitleMatch.range.first)
                truncated = true
            } else if (explicitEpisodeMatch != null) {
                name = name.substring(0, explicitEpisodeMatch.range.first)
                truncated = true
            } else if (dashEpisodeMatch != null) {
                name = name.substring(0, dashEpisodeMatch.range.first)
                truncated = true
            } else {
                val episodeMatch = "(?:(?<=^|[^a-zA-Z0-9])(?:Episode|Ep\\.?|E)|(?:\\s-\\s|\\s|-|\\.))([0-9]{1,3})(?=[^a-zA-Z0-9]|$)".toRegex(RegexOption.IGNORE_CASE).find(name)
                if (episodeMatch != null) {
                    name = name.substring(0, episodeMatch.range.first)
                    truncated = true
                } else {
                    val standaloneEpisodeMatch = standaloneEpisodePattern.find(name)
                    if (standaloneEpisodeMatch != null && yearPattern.find(standaloneEpisodeMatch.value) == null) {
                        name = name.substring(0, standaloneEpisodeMatch.range.first)
                        truncated = true
                    } else {
                        val yearMatch = yearPattern.find(name)
                        if (yearMatch != null) {
                            name = name.substring(0, yearMatch.range.first)
                            truncated = true
                        }
                    }
                }
            }
        }

        name = name.replace(bracketPattern, "").trim()
        name = name.replace(".", " ").replace("_", " ").trim()
        name = name.replace(Regex("\\s*\\+\\s*(?:extras|bonus|specials)\\b", RegexOption.IGNORE_CASE), " ")
        name = name.replace(Regex("(?<=[a-zA-Z\\s])[0-9]{1,3}-[0-9]{1,3}(?=\\s*(?:\\+|$))"), " ")
        name = name.replace(Regex("\\s+"), " ").trim()

        // Remove trailing episode keywords and trailing dashes or separators
        name = name.replace(Regex("(?i)(?:\\s*-\\s*|\\s+)(?:Episode|Ep|E)\\.?$"), "").trim()
        while (name.endsWith("-") || name.endsWith(".")) {
            name = name.substring(0, name.length - 1).trim()
        }
        name = name.replace(Regex("(?i)(?:\\s*-\\s*|\\s+)(?:Episode|Ep|E)\\.?$"), "").trim()
        while (name.endsWith("-") || name.endsWith(".")) {
            name = name.substring(0, name.length - 1).trim()
        }

        if (!truncated) {
            // If extracted name ends with a number that looks like an episode (e.g., show-24)
            // and we haven't truncated it yet, do a last resort truncation
            val trailingEpisodePattern = "(?<=[a-zA-Z\\s])-(?:ep)?([0-9]{1,3})$".toRegex(RegexOption.IGNORE_CASE)
            val trailingMatch = trailingEpisodePattern.find(name)
            if (trailingMatch != null) {
                name = name.substring(0, trailingMatch.range.first).trim()
            }
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
        if (normalized.matches("^[a-z]{1,4}\\s?[0-9]{1,3}$".toRegex())) return true
        if (normalized.matches("^[0-9]+\\s?[a-z]{2,3}$".toRegex())) return true // e.g. "6 spa", "6_spa"

        // Auxiliary exact keywords
        val auxiliaryExactKeywords = setOf(
            "subs", "sub", "subtitles", "subtitle", "vobsub", "idx", "srt",
            "extras", "extra", "featurettes", "featurette", "bonus", "bonuses",
            "sample", "samples", "proof", "proofs", "screenshot", "screenshots", "screens cap",
            "trailer", "trailers", "teaser", "teasers", "promo", "promos", "promotional",
            "deleted scenes", "deleted scene", "deleted", "deletedscenes",
            "alternate ending", "alternate endings", "alternate opening", "alternate openings", "alternate cut",
            "alternate scene", "alternate scenes", "extended scene", "extended scenes", "extended cut",
            "outtakes", "outtake", "bloopers", "blooper", "gag reel", "gag reels", "gagreel", "gagreels",
            "behind the scenes", "behindthescenes", "making of", "makingof",
            "screentests", "screentest", "interview", "interviews", "cast", "crew",
            "character profile", "character profiles", "profiles", "profile",
            "original scripts", "script", "scripts",
            "covers", "cover", "artwork", "artworks", "art", "fonts", "font", "scans", "scan", "covers and fonts",
            "soundtrack", "soundtracks", "ost", "music video", "music videos", "music", "audio",
            "metadata", "nfo", "info", "other", "misc", "miscellaneous",
            "special", "specials", "sp", "ova", "ona", "special feature", "special features",
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
            "season ", "seasons ", "disc ", "disk ", "cd ", "dvd ",
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
        val hasKnownJunkOrTechToken = tokens.any { allTechnicalAndJunkTokens.contains(it) }
        if (hasKnownJunkOrTechToken && tokens.all { token ->
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

        val auxiliaryFolderKeywords = setOf(
            "extras", "extra", "featurettes", "featurette", "bonus", "bonuses",
            "samples", "sample", "screenshots", "screenshot", "screens cap", "screens",
            "trailers", "trailer", "teasers", "teaser", "promos", "promo",
            "deleted scenes", "deleted scene", "screentests", "screentest",
            "subs", "subtitles", "sub", "original scripts", "soundtracks", "soundtrack",
            "ost", "artwork", "covers", "fonts", "scans", "proofs", "specials", "special",
            "outtakes", "outtake", "bloopers", "blooper", "gag reel", "behind the scenes", "making of"
        )

        val lowerCleaned = cleanedFileName.lowercase()
        val auxiliaryFileKeywords = listOf(
            "alternate ending", "alternate endings", "alternate opening", "alternate openings", "alternate cut",
            "alternate scene", "alternate scenes", "extended scene", "extended scenes", "extended cut",
            "featurette", "featurettes", "trailer", "trailers", "teaser", "teasers", "promo", "promos",
            "deleted scene", "deleted scenes", "screentest", "screentests", "outtake", "outtakes",
            "blooper", "bloopers", "gag reel", "gag reels", "screenshot", "screenshots", "sample", "samples",
            "character profile", "character profiles", "behind the scenes", "making of", "interview", "interviews",
            "original script", "original scripts", "textless", "special feature", "special features", "bonus", "bonuses"
        )
        if (auxiliaryFileKeywords.any { lowerCleaned.contains(it) }) {
            val fileNameWithoutExt = file.nameWithoutExtension
            if (seasonPattern.find(fileNameWithoutExt) == null && seasonPattern2.find(fileNameWithoutExt) == null) {
                return true
            }
        }

        var p = file.parentFile
        var depth = 0
        while (p != null && depth < 6) {
            val pName = decodeIfEncoded(p.name).lowercase().trim()
            val pNormalized = pName.replace("_", " ").replace(".", " ").replace("-", " ")
            val pTokens = pNormalized.split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }

            val isPackFolder = pName.contains("+") || pNormalized.contains("with extras") || pNormalized.contains("and extras") ||
                    pNormalized.matches(".*\\b(?:season|s[0-9]+|[0-9]+-[0-9]+)\\s*(?:\\+|plus|with|and)\\s*(?:extras|bonus).*".toRegex())

            if (!isPackFolder) {
                if (auxiliaryFolderKeywords.contains(pName) || auxiliaryFolderKeywords.contains(pNormalized)) {
                    return true
                }
                if (pTokens.size <= 3 && pTokens.any { auxiliaryFolderKeywords.contains(it) }) {
                    return true
                }
            }
            p = p.parentFile
            depth++
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

        // Category / root folder names
        val categoryFolderNames = setOf(
            "series-shows", "series shows", "series", "shows", "tv", "tv shows", "tv show", "tv-shows",
            "tv series", "tv-series", "movies", "movie", "films", "film", "anime",
            "cartoons", "cartoon", "documentaries", "documentary", "downloads", "download",
            "torrents", "torrent", "complete", "incomplete", "pfdb", "data", "videos",
            "video", "media", "home", "root", "desktop"
        )

        // If filename is junk or auxiliary, search upwards in parent directory hierarchy
        val candidates = mutableListOf<String>()
        var currentFile: File? = file.parentFile
        var depth = 0
        while (currentFile != null && depth < 5) {
            val parentName = decodeIfEncoded(currentFile.name)
            val lowerParent = parentName.lowercase().trim()
            val normalizedParent = lowerParent.replace("_", " ").replace(".", " ").replace("-", " ").trim()

            // Stop traversing upwards if we reached a single letter folder, symbol folder, range folder, or root category / filesystem folder
            if (parentName.trim().length <= 1 ||
                parentName.trim().all { !it.isLetterOrDigit() } ||
                lowerParent.matches("^[0-9]-[0-9]$".toRegex()) ||
                lowerParent.matches("^[a-z]-[a-z]$".toRegex()) ||
                categoryFolderNames.contains(lowerParent) ||
                categoryFolderNames.contains(normalizedParent)
            ) {
                break
            }

            val cleanedParentName = cleanName(parentName)
            if (cleanedParentName.isNotBlank() && !isJunk(cleanedParentName)) {
                candidates.add(cleanedParentName)
            }
            currentFile = currentFile.parentFile
            depth++
        }

        if (candidates.isNotEmpty()) {
            val reversed = candidates.reversed()
            var combined = reversed.first()
            for (i in 1 until reversed.size) {
                val next = reversed[i]
                val lowerCombined = combined.lowercase()
                val lowerNext = next.lowercase()
                if (!lowerCombined.contains(lowerNext) && !lowerNext.contains(lowerCombined)) {
                    combined = "$combined $next"
                } else if (lowerNext.contains(lowerCombined)) {
                    combined = next
                }
            }
            return cleanName(combined)
        }

        return ""
    }

    fun extractYear(fullName: String): Int? {
        val yearPattern = "\\b([1-2][0-9]{3})\\b".toRegex()
        val match = yearPattern.find(File(fullName).name) ?: yearPattern.find(File(fullName).parentFile?.name ?: "")
        return match?.groupValues?.get(1)?.toInt()
    }

    fun extractSeason(file: File): Int? {
        val seasonPattern = "(?<=^|[^a-zA-Z])S([0-9]{1,2})(?=[^a-zA-Z0-9]|$|E[0-9]{1,2})".toRegex(RegexOption.IGNORE_CASE)
        val seasonPatternInName = "(?<=Season\\s?)([0-9]{1,2})".toRegex(RegexOption.IGNORE_CASE)
        val seasonPattern2 = "(?:\\[|\\b)([0-9]{1,2})x([0-9]{1,2})(?:\\]|\\b)".toRegex()
        val dvdPattern = "(?<=^|\\s)(?:dvd|disc|cd|book|volume|vol)\\s?([0-9]{1,2})".toRegex(RegexOption.IGNORE_CASE)

        // Check seasonPattern2 (e.g. [2x01]) first across file and parents
        var current: File? = file
        var depth = 0
        while (current != null && depth < 6) {
            val name = decodeIfEncoded(current.name)
            val match2 = seasonPattern2.find(name)
            if (match2 != null) {
                return match2.groupValues[1].toInt()
            }
            current = current.parentFile
            depth++
        }

        // Check seasonPattern (e.g. S01), seasonPatternInName (e.g. Season 1), dvdPattern
        current = file
        depth = 0
        while (current != null && depth < 6) {
            val name = decodeIfEncoded(current.name)
            val match = seasonPattern.find(name)
                ?: seasonPatternInName.find(name)
                ?: dvdPattern.find(name)
            if (match != null) {
                return match.groupValues[match.groupValues.size - 1].toInt()
            }
            current = current.parentFile
            depth++
        }

        return null
    }

    fun extractEpisode(file: File): Int? {
        val fileName = decodeIfEncoded(file.name)
        val parentName = decodeIfEncoded(file.parentFile?.name ?: "")
        val grandParentName = decodeIfEncoded(file.parentFile?.parentFile?.name ?: "")
        val greatGrandParentName = decodeIfEncoded(file.parentFile?.parentFile?.parentFile?.name ?: "")

        val episodeWithTitlePattern = "(?:\\s|^)(?:Episode|Ep\\.?|E)?\\s*([0-9]{1,3})\\s+-\\s+[a-zA-Z]".toRegex(RegexOption.IGNORE_CASE)
        val dashEpisodePattern = "(?:\\s-\\s|\\s-_|_-_|\\s-\\s?|_-\\s?)([0-9]{1,3})(?=[^a-zA-Z0-9]|$)".toRegex()
        val explicitEpisodePattern = "(?<=^|[^a-zA-Z0-9])(?:Episode|Ep\\.?|E)\\s*([0-9]{1,3})(?=[^a-zA-Z0-9]|$)".toRegex(RegexOption.IGNORE_CASE)
        val episodePattern =
            "(?:(?<=^|[^a-zA-Z0-9])(?:Episode|Ep\\.?|E)|(?:\\s-\\s|\\s|-|\\.))([0-9]{1,3})(?=[^a-zA-Z0-9]|$)".toRegex(RegexOption.IGNORE_CASE)
        val standaloneEpisodePattern = "(?:\\s|^)([0-9]{3})(?:\\s|$)".toRegex()
        val yearPattern = "\\b([1-2][0-9]{3})\\b".toRegex()
        val seasonEpisodePattern = "(?<=^|[^a-zA-Z])S[0-9]{1,2}E([0-9]{1,2})".toRegex(RegexOption.IGNORE_CASE)
        val seasonPattern2 = "(?:\\[|\\b)([0-9]{1,2})x([0-9]{1,2})(?:\\]|\\b)".toRegex()
        val acronymEpisodePattern = "(?<=^|[^a-zA-Z0-9])([a-zA-Z]{1,4})([0-9]{1,3})(?=[^a-zA-Z0-9]|$)".toRegex(RegexOption.IGNORE_CASE)
        val excludedAcronyms = setOf(
            "x264", "h264", "x265", "h265", "hevc", "avc", "xvid", "dvd", "disc", "cd", "bd",
            "part", "pt", "vol", "v", "ch", "aac", "mp3", "mp4", "ac3", "fps", "kbps", "mbps",
            "s", "se", "season", "dvdrip", "bdrip", "brrip", "webrip", "webdl", "hdtv", "pdtv"
        )
        val replaced = fileName.replace("_", " ")

        val match2 = seasonPattern2.find(fileName)
            ?: seasonPattern2.find(parentName)
            ?: seasonPattern2.find(grandParentName)
            ?: seasonPattern2.find(greatGrandParentName)

        if (match2 != null) {
            return match2.groupValues[2].toInt()
        }

        val rawBaseName = fileName.substringBeforeLast(".").replace(bracketPattern, "").trim()
        val acronymMatch = acronymEpisodePattern.findAll(rawBaseName).firstOrNull { m ->
            val prefix = m.groupValues[1].lowercase()
            val full = m.value.lowercase()
            !excludedAcronyms.contains(prefix) && !excludedAcronyms.contains(full) && prefix != "s" && prefix != "se"
        }

        val nonEpisodePrefix = "(?i)(?:season|series|disc|disk|cd|dvd|bd|volume|vol|book|chapter|part|pt)\\s*$".toRegex()
        val firstValidEpisodeMatch = episodePattern.findAll(replaced)
            .plus(episodePattern.findAll(fileName))
            .firstOrNull { m ->
                val textBefore = replaced.substring(0, m.range.first).trim()
                !nonEpisodePrefix.containsMatchIn(textBefore)
            }

        val match = seasonEpisodePattern.find(fileName)
            ?: seasonEpisodePattern.find(parentName)
            ?: seasonEpisodePattern.find(grandParentName)
            ?: seasonEpisodePattern.find(greatGrandParentName)
            ?: explicitEpisodePattern.find(fileName)
            ?: episodeWithTitlePattern.find(fileName)
            ?: episodeWithTitlePattern.find(replaced)
            ?: dashEpisodePattern.find(fileName)
            ?: dashEpisodePattern.find(replaced)
            ?: "-([0-9]{1,3})\\.".toRegex().find(fileName)
            ?: acronymMatch
            ?: standaloneEpisodePattern.find(fileName)?.let { if (yearPattern.find(it.value) == null) it else null }
            ?: firstValidEpisodeMatch
            ?: explicitEpisodePattern.find(parentName)
            ?: explicitEpisodePattern.find(grandParentName)

        if (match != null) {
            val groupIdx = if (match.groupValues.size > 1) match.groupValues.size - 1 else 0
            return match.groupValues[groupIdx].toIntOrNull()
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
                    val extractedName = extractName(file)
                    val year = extractYear(file.absolutePath)
                    var season = extractSeason(file)
                    val episode = extractEpisode(file)
                    
                    val mediaIndicator = if(episode != null) MediaType.Series else null

                    val mediaResult = if (extractedName.isNotBlank()) {
                        if (year != null) {
                            mediaProxyService.search(extractedName, year, mediaIndicator).firstOrNull()
                        } else {
                            mediaProxyService.search(extractedName, mediaIndicator).firstOrNull()
                        }
                    } else {
                        null
                    }

                    if (season == null && episode != null && mediaResult?.type == MediaType.Series) {
                        season = 1
                    }

                    val idString = mediaResult?.id ?: ""
                    var logLine =
                        "F: '$fullName'->'$extractedName'|>'$mediaResult' (Y: $year, S: $season, E: $episode) $idString"
                    mediaResult?.let {
                        logLine = logLine+"\n"+getTargetFile(file, mediaResult, extractedName, season, episode)
                    }
                    println(logLine)
                    logFile?.appendText(logLine + "\n")
                } else {
                    processFilesFromDirectory(file.absolutePath, logFile)
                }
            }
        }
    }

    fun extractResolution(file: File): String? {
        val resolutionPattern = "(?i)(?<=^|[^a-zA-Z0-9])(2160p|1080p|1080i|720p|576p|576i|480p|480i|4k)(?=[^a-zA-Z0-9]|$)".toRegex()
        var current: File? = file
        var depth = 0
        while (current != null && depth < 4) {
            val name = decodeIfEncoded(current.name)
            val match = resolutionPattern.find(name)
            if (match != null) {
                return match.groupValues[1].lowercase()
            }
            current = current.parentFile
            depth++
        }
        return null
    }

    fun extractCodec(file: File): String? {
        val codecPattern = "(?i)(?<=^|[^a-zA-Z0-9])(x265|x264|h265|h264|hevc|avc|xvid|divx|av1|vp9|h\\.264|h\\.265|x\\.264|x\\.265)(?=[^a-zA-Z0-9]|$)".toRegex()
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

    fun getTargetFile(file: File, mediaResult: MediaItem, extractedName: String, season: Int?, episode: Int?): String {
        val firstPart = "~/pfdb/PFDB/" + getMediaTypeFolderString(mediaResult) +
                "/" + StringUtil.deductFirstTorrentLetter(getSafeTitle(mediaResult)) + "/${getSafeTitle(mediaResult)} ${mediaResult.id}"
        val optionalSeasonFolder = if(season != null){
             "/Season ${season}"
        } else {
            ""
        }
        val optionalFileNaming = if(episode != null){
            val seasonStr = String.format("%02d", season ?: 1)
            val episodeStr = String.format("%02d", episode)
            val resolution = extractResolution(file)
            val codec = extractCodec(file)
            val extraInfo = listOfNotNull(resolution, codec).joinToString(".")
            val extraSuffix = if (extraInfo.isNotEmpty()) ".$extraInfo" else ""
            "/${extractedName}.S${seasonStr}E${episodeStr}$extraSuffix"
        } else if (season != null || mediaResult.type == MediaType.Series) {
            "/${file.nameWithoutExtension}"
        } else {
            ""
        }
        val fileExtension = file.name.replace(file.nameWithoutExtension, "")
        return firstPart + optionalSeasonFolder+optionalFileNaming+fileExtension
    }

    fun getSafeTitle(mediaResult: MediaItem): String {
        return getSafeTitle(mediaResult.title)
    }

    fun getSafeTitle(title: String): String {
        return title.replace("//", " ").replace(Regex("[\\\\/:*?\"<>|]"), "")
    }

    private fun getMediaTypeFolderString(mediaResult: MediaItem): String = when (mediaResult.type) {
        MediaType.Movie -> "Movies"
        MediaType.Series -> "Series-Shows"
        MediaType.Documentary -> "Documentaries"
        else -> "transfer"
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