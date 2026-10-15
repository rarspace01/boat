package boat

import boat.info.MediaItem
import boat.info.MediaProxyService
import boat.info.MediaType
import boat.utilities.StringUtil
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.io.path.moveTo

class ContentRenamer(val mediaProxyService: MediaProxyService) {

    companion object {
        val seasonPattern = "(?<=^|[^a-zA-Z])S([0-9]{1,2})(?:[x._\\-\\s]?(?:E|EP)[0-9]{1,3})?".toRegex(RegexOption.IGNORE_CASE)
        val seasonPattern2 = "(?:\\[|\\b)([0-9]{1,2})x([0-9]{1,2})(?:\\]|\\b)".toRegex()
        val episodePattern =
            "(?:(?<=^|[^a-zA-Z0-9])(?:Episode|Ep\\.?|E)|(?:\\s-\\s|\\s|-|\\.))([0-9]{1,3})(?=[^a-zA-Z0-9]|$)".toRegex(RegexOption.IGNORE_CASE)
        val standaloneEpisodePattern = "(?<=^|\\s)(?<![0-9])([0-9]{3})(?![0-9])(?=\\s|$)".toRegex()
        val yearPattern = "\\b(19[0-9]{2}|20[0-2][0-9])\\b".toRegex()
        val bracketPattern = "\\[.*?]".toRegex()
        val dvdPattern = "(?<=^|\\s)(?:dvd|disc|cd)\\s?([0-9]{1,2})".toRegex(RegexOption.IGNORE_CASE)
        val bookPattern = "(?<=^|\\s)(?:book|volume|vol|chapter)\\s?([0-9]{1,2})".toRegex(RegexOption.IGNORE_CASE)
        val seasonInNamePattern = "(?<=^|[\\s_.-])(?:Season|Seasons)[\\s_.-]*(?:S?[0-9]{1,4}|[0-9]{1,2}[-–_~to]+(?:S?[0-9]{1,2})?|S[0-9]{2}[-–_~to]?S?[0-9]{2})?".toRegex(RegexOption.IGNORE_CASE)
        val dayInNamePattern = "(?<=^|[\\s_.-])Day\\s([0-9]{1,2})".toRegex(RegexOption.IGNORE_CASE)
        val packInNamePattern = "(?<=^|[\\s_.-])(?:Complete(?:[\\s_.-]+(?:Series|Season|Seasons|Collection|Set|Pack|Boxset|Box[\\s_.-]+Set|With[\\s_.-]+Subtitles))?|Completo|All[\\s_.-]+(?:Series|Season|Seasons|Episodes|Eps|Collection|Set|Pack|Boxset|Box[\\s_.-]+Set)|Entire[\\s_.-]+(?:Series|Season|Seasons|Collection|Set|Pack|Boxset|Box[\\s_.-]+Set)|Full[\\s_.-]+(?:Series|Season|Seasons|Collection|Set|Pack|Boxset|Box[\\s_.-]+Set)|(?:Boxset|Box[\\s_.-]+Set|Collection|Collector(?:'s|s)?[\\s_.-]+Edition)|With[\\s_.-]+(?:Subtitles|Subs)|Dual[\\s_.-]+Audio|Multi[- ]Audio|Multi[- ]Subs|Tv[\\s_.-]+Series(?:[\\s_.-]+(?:Season|Seasons))?)(?=\\b|[\\s_.-]|$)".toRegex(RegexOption.IGNORE_CASE)
        val extrasInNamePattern = "(?<=^|[\\s_.-])(?:Extras|Extra|Featurettes|Featurette|Bonus|Subs|Subtitles|Gallery|Galleries)(?=\\b|[\\s_.-]|$)".toRegex(RegexOption.IGNORE_CASE)
        val startsWithEpisodeNumberPattern = "^(?:[0-9]{1,3}\\s?[.x-]\\s?[0-9]{1,3}\\s?-\\s?|(?:19[0-9]{2}|20[0-2][0-9])[eExX][0-9]{1,3}[-._\\s]?|S[0-9]{1,2}[x._\\-\\s]?(?:E|EP)[0-9]{1,3}[-._\\s]?|[0-9]{1,2}x[0-9]{1,2}[-._\\s]?)".toRegex(RegexOption.IGNORE_CASE)
    }

    fun cleanName(inputName: String): String {
        var name = inputName
        // Normalize unicode and foreign characters, remove backslash escape characters
        name = name.replace("：", " ").replace("?", " ").replace("\\", "")

        // Remove bracketed release info like [UNCENSORED], [1080p], [5.1 MP3], [x265], [FINAL]
        name = name.replace(bracketPattern, " ").trim()

        // Remove common release group/site prefixes
        val sitePrefixPattern = "^(www\\..*?\\.(?:com|org|net|me|to|tv|re|io)\\s-\\s)".toRegex(RegexOption.IGNORE_CASE)
        name = name.replace(sitePrefixPattern, "")

        // Remove release tag prefix like (B-A) or [GAX]
        val parenthesizedPrefixPattern = "^\\([a-zA-Z0-9-]+\\)".toRegex()
        name = name.replace(parenthesizedPrefixPattern, "")

        val groupPrefixPattern = "^([0-9a-f]{4,10}-)".toRegex(RegexOption.IGNORE_CASE)
        // check if it's a known hex garbage prefix (containing digits)
        val match = groupPrefixPattern.find(name)
        if (match != null && match.value.any { it.isDigit() } && !name.lowercase().startsWith("star") && !name.lowercase().startsWith("hack") &&
            !name.lowercase().matches("^[0-9]+-(?:lb|lb\\.|pound|foot|inch|mile|meter|gram|ton|year|day|hour|min|sec|part|way|ways).*".toRegex())
        ) {
            name = name.replace(groupPrefixPattern, "")
        }

        // Remove parenthesized tags like (BBC 1995), (2013 BluRay - 1080p DUAL AUDIO), (ger.sub), (h264), (moviesbyrizzo), (720p - DUAL Audio), (1BB88E0D)
        val parenthesizedTagsPattern = "(?i)\\((?:[^)]*\\b(?:[0-9]{3,4}p|bluray|blu-ray|dvd|bdrip|dvdrip|web-dl|webdl|webrip|h264|x264|h265|x265|hevc|xvid|divx|dual audio|multi audio|multi subs|proper|repack|sub|subs|ger[._ -]?sub|eng[._ -]?sub|moviesbyrizzo|bbc[._ -]?[0-9]{4}|[0-9]{4}\\s*-\\s*[0-9]{2,4}|open matte version|theatrical version|subbed|french|korean|german|japanese|spanish|[0-9a-f]{8})\\b[^)]*)\\)".toRegex()
        name = name.replace(parenthesizedTagsPattern, " ")

        val parenthesizedEpisodeRangePattern = "\\([0-9]{1,3}-[0-9]{1,3}\\)".toRegex()
        name = name.replace(parenthesizedEpisodeRangePattern, " ")

        val parenthesizedYearPattern = "[(\\[](?:19[0-9]{2}|20[0-2][0-9])(?:-[0-9]{2,4})?[)\\]]".toRegex()
        val hasParenthesizedYear = parenthesizedYearPattern.containsMatchIn(name)
        name = name.replace(parenthesizedYearPattern, " ")
        name = name.replace("(", "").replace(")", "").replace("_", " ").trim()
        name = name.removePrefix(".")

        val yearEpisodePrefixPattern = "^(?:19[0-9]{2}|20[0-2][0-9])[eExX][0-9]{1,3}[-._\\s]?".toRegex(RegexOption.IGNORE_CASE)
        name = name.replace(yearEpisodePrefixPattern, "")

        // Remove version markers like v2 in 01v2
        name = name.replace("(?<=[0-9])v[0-9]+".toRegex(), "")

        var truncated = false
        val seasonMatch = seasonPattern.find(name)
        val seasonMatch2 = seasonPattern2.find(name)
        val seasonMatch3 = seasonInNamePattern.find(name)
        val dayMatch = dayInNamePattern.find(name)
        val dvdMatch = dvdPattern.find(name)
        val bookMatch = bookPattern.find(name)
        val packMatch = packInNamePattern.find(name)
        val extrasMatch = extrasInNamePattern.find(name)

        val potentialSeasonMatches = listOfNotNull(
            seasonMatch?.range?.first,
            seasonMatch2?.range?.first,
            seasonMatch3?.range?.first,
            if (dayMatch != null && dayMatch.range.first > 0) dayMatch.range.first else null,
            dvdMatch?.range?.first,
            bookMatch?.range?.first,
            if (packMatch != null && packMatch.range.first > 0) packMatch.range.first else null,
            if (extrasMatch != null && extrasMatch.range.first > 0) extrasMatch.range.first else null,
        )

        val earliestMatchIndex = potentialSeasonMatches.minOrNull()
        if (earliestMatchIndex != null) {
            name = name.substring(0, earliestMatchIndex)
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
                val episodeMatch = "(?:(?<=^|[^a-zA-Z0-9])(?:Episode|Ep\\.?|E)|(?:\\s-\\s|\\s-_|_-_|\\s-\\s?|_-\\s?|-(?=[0-9])|\\.(?=0[0-9])))([0-9]{1,3})(?=[^a-zA-Z0-9]|$)".toRegex(RegexOption.IGNORE_CASE).find(name)
                if (episodeMatch != null) {
                    name = name.substring(0, episodeMatch.range.first)
                    truncated = true
                } else {
                    val standaloneEpisodeMatch = standaloneEpisodePattern.find(name)
                    val zeroPaddedMatch = "(?:\\s|^)0([0-9]{1,3})(?:\\s|$)".toRegex().find(name)
                    if (zeroPaddedMatch != null) {
                        name = name.substring(0, zeroPaddedMatch.range.first)
                        truncated = true
                    } else if (standaloneEpisodeMatch != null && yearPattern.find(standaloneEpisodeMatch.value) == null) {
                        val beforeMatch = name.substring(0, standaloneEpisodeMatch.range.first).trim().lowercase()
                        val articles = setOf("the", "a", "an", "der", "die", "das", "le", "la", "les", "el", "los", "las", "il", "lo", "gli", "un", "une")
                        if (!articles.contains(beforeMatch)) {
                            name = name.substring(0, standaloneEpisodeMatch.range.first)
                            truncated = true
                        }
                    } else {
                        val yearMatch = yearPattern.find(name)
                        if (yearMatch != null && !hasParenthesizedYear && yearMatch.range.first > 0) {
                            name = name.substring(0, yearMatch.range.first)
                            truncated = true
                        }
                    }
                }
            }
        }

        name = name.replace(bracketPattern, "").trim()
        val acronymPattern = "(?<=(?:^|[^a-zA-Z0-9]))(?:[a-zA-Z]\\.){1,}[a-zA-Z](?:\\.(?=[^a-zA-Z0-9]|$)|(?=[^a-zA-Z0-9]|$))".toRegex()
        name = name.replace(acronymPattern) { matchResult ->
            matchResult.value.replace(".", "")
        }
        name = name.replace(".", " ").replace("_", " ").trim()
        name = name.replace(Regex("\\s*\\+\\s*(?:extras|bonus|specials)\\b", RegexOption.IGNORE_CASE), " ")
        name = name.replace(Regex("(?<=[a-zA-Z\\s])[0-9]{1,3}-[0-9]{1,3}(?=\\s*(?:\\+|$))"), " ")
        name = name.replace(Regex("\\s+"), " ").trim()

        val trailingQualityTag = "(?i)\\s+(?:720p|1080p|1080i|2160p|480p|4k|uhd|fhd|hdtv|bluray|blu-ray|bdrip|dvdrip|dvd|web-dl|webdl|webrip|web|remux|proper|repack|rerip|x264|x265|h264|h265|hevc|xvid|dual audio|multi audio|multi subs|subbed|korean|french|german|japanese|spanish|english|director'?s cut|extended cut|uncensored|remastered|tv series|season|seasons|complete series|complete season|all seasons)$".toRegex()
        val trailingYearPattern = "(?<=[a-zA-Z\\s])\\s+(?:19[0-9]{2}|20[0-2][0-9])(?:-[0-9]{2,4})?$".toRegex()

        val trailingPunctuation = setOf('-', '.', ',', ';', ':', '_', '~', '+', '!', '?', '\'', '"', '`', '/', '\\')
        while (true) {
            val before = name
            name = name.replace(trailingQualityTag, "").trim()
            if (!hasParenthesizedYear) {
                name = name.replace(trailingYearPattern, "").trim()
            }
            name = name.replace(Regex("(?i)(?:\\s*-\\s*|\\s+)(?:Episode|Ep|E)\\.?$"), "").trim()
            while (name.isNotEmpty() && name.last() in trailingPunctuation) {
                name = name.dropLast(1).trim()
            }
            if (name == before) break
        }
        while (name.isNotEmpty() && name.first() in trailingPunctuation) {
            name = name.drop(1).trim()
        }

        if (!truncated) {
            // If extracted name ends with a number that looks like an episode (e.g., show-24)
            // and we haven't truncated it yet, do a last resort truncation
            val trailingEpisodePattern = "(?<=[a-zA-Z\\s])-(?:ep)?([0-9]{1,3})$".toRegex(RegexOption.IGNORE_CASE)
            val trailingMatch = trailingEpisodePattern.find(name)
            if (trailingMatch != null) {
                name = name.substring(0, trailingMatch.range.first).trim()
            } else {
                // If name has a number followed by space and another number (e.g. Golgo 13 20), truncate the trailing episode
                val multiNumberTrailingPattern = "(?<=[0-9])\\s+([0-9]{1,3})$".toRegex()
                val multiMatch = multiNumberTrailingPattern.find(name)
                if (multiMatch != null) {
                    name = name.substring(0, multiMatch.range.first).trim()
                }
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
        val articles = setOf("the", "a", "an", "and", "or", "of", "in", "with", "&", "der", "die", "das", "le", "la", "les", "el", "los", "las", "il", "lo", "gli", "un", "une")
        if (articles.contains(lower) || articles.contains(normalized)) return true

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
        if (normalized.matches("^(?:uk|us|usa|ger|fra|fr|de|es|ita|it|jp|jpn|kr|kor|cn|chi|ru|rus|eng|en)?\\s*(?:season|series|disc|disk|cd|dvd|bd|volume|vol|book|chapter|part|ep|episode|day)\\s*[0-9]+.*$".toRegex())) return true
        if (normalized.matches("^s[0-9]{1,2}(?:\\s?e[0-9]{1,2})?$".toRegex())) return true
        if (normalized.matches("^[0-9]{1,2}\\s?x\\s?[0-9]{1,2}$".toRegex())) return true
        if (normalized.matches("^[0-9]{1,3}\\s?[.\\sx-]\\s?[0-9]{1,3}$".toRegex())) return true
        if (normalized.matches("^[a-z]{1,4}\\s?[0-9]{1,3}$".toRegex()) && !normalized.startsWith("the ") && !normalized.startsWith("a ") && !normalized.startsWith("an ")) return true
        if (normalized.matches("^[0-9]+\\s?[a-z]{2,3}$".toRegex())) return true // e.g. "6 spa", "6_spa"

        val countryAndLanguageTags = setOf(
            "uk", "us", "usa", "ger", "fra", "fr", "de", "es", "ita", "it", "jp", "jpn", "kr", "kor", "cn", "chi", "ru", "rus", "eng", "en"
        )
        if (countryAndLanguageTags.contains(lower) || countryAndLanguageTags.contains(normalized)) return true

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
            "special", "specials", "sp", "ova", "ovas", "ona", "onas", "special feature", "special features",
            "textless", "textless opening", "textless ending", "ncop", "nced", "creditless",
            "episode guide", "guide", "cfwm", "teneighty", "greatest saga",
            "fake psas", "fake psa", "tv spots", "tv spot", "storyboard comparison", "tv recap specials",
            "new", "lessons", "videolessons", "intermediate", "advanced", "survival phrases", "audio blog",
            "gallery", "galleries", "photo gallery", "image gallery", "still gallery", "stills gallery", "slideshow", "slideshows",
            "story", "stories", "storyline", "temporada", "temporadas", "season", "seasons", "episode", "episodes",
            "collection", "collections", "boxset", "pack", "full collection", "complete collection"
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
            "screenshot ", "screenshots ", "deleted scene", "deleted scenes", "making of ", "behind the scenes ",
            "story ", "temporada ", "lesson ", "lessons "
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
            "complete", "season", "seasons", "series", "ep", "episode", "episodes", "disc", "disk", "cd", "vol", "volume",
            "movie", "movies", "special", "specials", "ova", "ovas", "ona", "onas", "collection", "collections", "boxset", "pack"
        )

        val tokenNormalized = trimmed.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
        val tokens = tokenNormalized.split(" ").filter { it.isNotBlank() }
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
            "ost", "artwork", "covers", "fonts", "scans", "proofs",
            "outtakes", "outtake", "bloopers", "blooper", "gag reel", "behind the scenes", "making of",
            "fake psas", "fake psa", "tv spots", "tv spot", "storyboard comparison", "tv recap specials",
            "lessons", "videolessons", "audio blog", "gallery", "galleries", "photo gallery", "image gallery", "still gallery", "stills gallery", "slideshow", "slideshows"
        )

        val lowerCleaned = cleanedFileName.lowercase()
        val auxiliaryFileKeywords = listOf(
            "alternate ending", "alternate endings", "alternate opening", "alternate openings", "alternate cut",
            "alternate scene", "alternate scenes", "extended scene", "extended scenes", "extended cut",
            "featurette", "featurettes", "trailer", "trailers", "teaser", "teasers", "promo", "promos",
            "deleted scene", "deleted scenes", "screentest", "screentests", "outtake", "outtakes",
            "blooper", "bloopers", "gag reel", "gag reels", "screenshot", "screenshots", "sample", "samples",
            "character profile", "character profiles", "behind the scenes", "making of", "interview", "interviews",
            "original script", "original scripts", "textless", "special feature", "special features", "bonus", "bonuses",
            "gallery", "galleries", "photo gallery", "image gallery", "still gallery", "stills gallery", "slideshow", "slideshows"
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

        val isNumericShowName = cleanedFileName.matches("^[0-9]+$".toRegex()) &&
                !cleanedFileName.startsWith("0") &&
                cleanedFileName != rawBaseName &&
                startsWithEpisodeNumberPattern.find(rawBaseName) == null

        val isCandidate0Junk = (isJunk(cleanedFileName) && !isNumericShowName) ||
                startsWithEpisodeNumberPattern.find(rawBaseName) != null ||
                rawBaseName.lowercase().startsWith("s0") ||
                rawBaseName.lowercase().startsWith("s1") ||
                rawBaseName.lowercase().startsWith("s2") ||
                rawBaseName.lowercase().startsWith("s3") ||
                isAuxiliaryPathOrFile(file, cleanedFileName)

        val isShortAcronym = cleanedFileName.matches("^[a-zA-Z]{2,6}$".toRegex()) &&
                (cleanedFileName == cleanedFileName.uppercase() || cleanedFileName.lowercase() in setOf("iasip", "btas", "tas", "cfwm", "stargatesg1"))

        // Category / root folder names
        val categoryFolderNames = setOf(
            "series-shows", "series shows", "series", "shows", "tv", "tv shows", "tv show", "tv-shows",
            "tv series", "tv-series", "movies", "movie", "films", "film", "anime",
            "cartoons", "cartoon", "documentaries", "documentary", "downloads", "download",
            "torrents", "torrent", "complete", "incomplete", "pfdb", "data", "videos",
            "video", "media", "home", "root", "desktop"
        )

        // Search upwards in parent directory hierarchy for candidates
        val candidates = mutableListOf<String>()
        var currentFile: File? = file.parentFile
        var depth = 0
        var isInsidePackOrSeriesFolder = false
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
                if (categoryFolderNames.contains(lowerParent) || categoryFolderNames.contains(normalizedParent)) {
                    if (lowerParent in setOf("series-shows", "series shows", "series", "shows", "tv", "tv shows", "tv-shows", "tv series", "tv-series", "cartoons", "cartoon", "anime")) {
                        isInsidePackOrSeriesFolder = true
                    }
                }
                break
            }

            if (packInNamePattern.containsMatchIn(parentName) ||
                parentName.contains("1080p", ignoreCase = true) ||
                parentName.contains("720p", ignoreCase = true) ||
                parentName.contains("x264", ignoreCase = true) ||
                parentName.contains("dvdrip", ignoreCase = true) ||
                parentName.contains("bluray", ignoreCase = true) ||
                parentName.contains("complete", ignoreCase = true) ||
                parentName.contains("season", ignoreCase = true)
            ) {
                isInsidePackOrSeriesFolder = true
            }

            val cleanedParentName = cleanName(parentName)
            val isParentNumericShowName = cleanedParentName.matches("^[0-9]+$".toRegex()) &&
                    !cleanedParentName.startsWith("0") &&
                    (parentName != cleanedParentName || currentFile.parentFile?.let { p ->
                        val pName = decodeIfEncoded(p.name).lowercase().trim()
                        pName.matches("^[0-9]-[0-9]$".toRegex()) || categoryFolderNames.contains(pName)
                    } == true)

            if (cleanedParentName.isNotBlank() && (!isJunk(cleanedParentName) && !isJunk(parentName) || isParentNumericShowName)) {
                candidates.add(cleanedParentName)
            }
            currentFile = currentFile.parentFile
            depth++
        }

        val parentShowName: String? = if (candidates.isNotEmpty()) {
            val reversed = candidates.reversed()
            var combined = reversed.first()
            for (i in 1 until reversed.size) {
                val next = reversed[i]
                val lowerCombined = combined.lowercase()
                val lowerNext = next.lowercase()
                val normCombined = lowerCombined.replace(Regex("[^a-z0-9]"), "")
                val normNext = lowerNext.replace(Regex("[^a-z0-9]"), "")
                if (normCombined == normNext ||
                    normCombined.startsWith(normNext) ||
                    normNext.startsWith(normCombined) ||
                    lowerCombined.contains(lowerNext) ||
                    lowerNext.contains(lowerCombined)
                ) {
                    if (lowerCombined.length >= lowerNext.length && !lowerCombined.contains("season")) {
                        // keep combined
                    } else if (!lowerNext.contains("season") && !lowerNext.contains("temporada")) {
                        combined = next
                    }
                } else {
                    val lowerNextCleaned = cleanName(next).lowercase()
                    val isNextAcronym = lowerNextCleaned in setOf("iasip", "btas", "tas", "cfwm", "sg1", "stargatesg1") ||
                            (lowerNextCleaned.length in 2..6 && lowerCombined.split(" ").mapNotNull { it.firstOrNull() }.joinToString("").lowercase() == lowerNextCleaned)
                    if (!isJunk(lowerNextCleaned) && !isNextAcronym && !lowerNextCleaned.startsWith("story ") && !lowerNextCleaned.startsWith("temporada ")) {
                        combined = "$combined $next"
                    }
                }
            }
            val result = combined.trim()
            if (result.lowercase() == "iasip") "Its Always Sunny In Philadelphia"
            else if (result.lowercase() == "stargatesg1") "Stargate SG-1"
            else result
        } else null

        if (!isCandidate0Junk && cleanedFileName.isNotBlank() && (!isShortAcronym || file.parentFile == null)) {
            if (parentShowName != null && parentShowName.isNotBlank()) {
                val remainder = cleanedFileName.removePrefix(parentShowName).trim()
                if (remainder.matches("^(?:[-_\\s]|ep|episode|e)*[0-9]{1,4}$".toRegex(RegexOption.IGNORE_CASE))) {
                    return parentShowName
                }
                val remainderParent = parentShowName.removePrefix(cleanedFileName).trim()
                if (remainderParent.matches("^(?:19[0-9]{2}|20[0-2][0-9]|[0-9]{1,4})$".toRegex())) {
                    return parentShowName
                }
                if (isInsidePackOrSeriesFolder) {
                    val parentWords = parentShowName.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length > 2 && it !in setOf("the", "and", "for", "with", "from") }
                    val fileWords = cleanedFileName.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length > 2 && it !in setOf("the", "and", "for", "with", "from") }
                    val hasOverlap = fileWords.any { fw -> parentWords.any { pw -> fw == pw || fw.contains(pw) || pw.contains(fw) } }
                    if (!hasOverlap) {
                        return parentShowName
                    }
                }
            }
            val result = cleanedFileName
            if (result.lowercase() == "iasip") return "Its Always Sunny In Philadelphia"
            if (result.lowercase() == "stargatesg1") return "Stargate SG-1"
            return result
        }

        if (parentShowName != null && parentShowName.isNotBlank()) {
            return parentShowName
        }

        return ""
    }

    fun extractYear(fullName: String): Int? {
        val parenthesizedYearRegex = "[(\\[](19[0-9]{2}|20[0-2][0-9])(?:-[0-9]{2,4})?[)\\]]".toRegex()
        val yearRegex = "(?<=^|[^0-9])(19[0-9]{2}|20[0-2][0-9])(?=[^0-9]|$)".toRegex()
        val file = File(fullName)

        // 1. Prefer explicit parenthesized year in file name
        val nameParenMatch = parenthesizedYearRegex.find(file.name)
        if (nameParenMatch != null) return nameParenMatch.groupValues[1].toInt()

        val parent = file.parentFile
        val isSeasonFolder = parent?.name?.let {
            "(?i)^(?:uk|us|usa|ger|fra|fr|de|es|ita|it|jp|jpn|kr|kor|cn|chi|ru|rus|eng|en)?[\\s_.-]*(?:Season|Series|Specials|Dis[ck]|DVD|CD|Vol(?:ume)?)(?:[\\s_.-]*[0-9]{1,4}|\\b)".toRegex().containsMatchIn(it)
        } ?: false

        // If parent is a season folder, check grandparent show folder first
        if (isSeasonFolder && parent?.parentFile != null) {
            val grandParentParenMatch = parenthesizedYearRegex.find(parent.parentFile.name)
            if (grandParentParenMatch != null) return grandParentParenMatch.groupValues[1].toInt()

            val grandParentMatch = yearRegex.find(parent.parentFile.name)
            if (grandParentMatch != null && grandParentMatch.range.first > 0) {
                return grandParentMatch.groupValues[1].toInt()
            }
            if (grandParentMatch != null) {
                return grandParentMatch.groupValues[1].toInt()
            }
        }

        // 2. Prefer explicit parenthesized year in parent folders
        var current: File? = parent
        var depth = 0
        while (current != null && depth < 5) {
            val parentParenMatch = parenthesizedYearRegex.find(current.name)
            if (parentParenMatch != null) return parentParenMatch.groupValues[1].toInt()
            current = current.parentFile
            depth++
        }

        // 3. Fall back to unparenthesized year (prefer match where index > 0)
        val nameMatch = yearRegex.find(file.name)
        if (nameMatch != null && nameMatch.range.first > 0) return nameMatch.groupValues[1].toInt()

        current = parent
        depth = 0
        while (current != null && depth < 5) {
            val currMatch = yearRegex.find(current.name)
            if (currMatch != null && currMatch.range.first > 0) {
                return currMatch.groupValues[1].toInt()
            }
            current = current.parentFile
            depth++
        }

        if (nameMatch != null) return nameMatch.groupValues[1].toInt()

        current = parent
        depth = 0
        while (current != null && depth < 5) {
            val currMatch = yearRegex.find(current.name)
            if (currMatch != null) {
                return currMatch.groupValues[1].toInt()
            }
            current = current.parentFile
            depth++
        }

        return null
    }

    fun extractSeason(file: File): Int? {
        val directSeason = extractDirectSeason(file)
        if (directSeason != null) return directSeason

        // If direct season is null, check if we can infer season from 3-digit/4-digit episode numbering (e.g. 101 -> season 1, 201 -> season 2, 0308 -> season 3)
        val rawEpisodeInfo = findRawEpisodeMatch(file)
        if (rawEpisodeInfo != null) {
            val (rawStr, num) = rawEpisodeInfo
            if (rawStr.length == 4 && rawStr.startsWith("0") && num in 101..9999) {
                val s = rawStr.substring(0, 2).toInt()
                val e = rawStr.substring(2, 4).toInt()
                if (s in 1..99 && e in 1..99) {
                    return s
                }
            }
            if (num in 101..9999 && !rawStr.startsWith("0")) {
                if (shouldDisambiguateAsSeasonEpisode(file, null, num, rawStr)) {
                    return num / 100
                }
            }
        }
        return null
    }

    fun extractDirectSeason(file: File): Int? {
        val seasonPattern = "(?<=^|[^a-zA-Z])S([0-9]{1,2})(?=[^a-zA-Z0-9]|$|[x._\\-\\s]?(?:E|EP)[0-9]{1,3})".toRegex(RegexOption.IGNORE_CASE)
        val seasonPatternInName = "(?<=(?:Season|Day)[\\s_.-]?)([0-9]{1,2})".toRegex(RegexOption.IGNORE_CASE)
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

        // Check seasonPattern (e.g. S01), seasonPatternInName (e.g. Season 1), dvdPattern, yearEpisodePattern
        val yearEpisodePattern = "(?<=^|[^a-zA-Z0-9])(19[0-9]{2}|20[0-2][0-9])[eExX][0-9]{1,3}(?=[^a-zA-Z0-9]|$)".toRegex(RegexOption.IGNORE_CASE)
        current = file
        depth = 0
        while (current != null && depth < 6) {
            val name = decodeIfEncoded(current.name)
            val match = seasonPattern.find(name)
                ?: seasonPatternInName.find(name)
                ?: dvdPattern.find(name)
                ?: yearEpisodePattern.find(name)
            if (match != null) {
                return match.groupValues[match.groupValues.size - 1].toInt()
            }
            current = current.parentFile
            depth++
        }

        return null
    }

    fun extractEpisode(file: File, mediaItem: MediaItem? = null): Int? {
        val fileName = decodeIfEncoded(file.name)
        val parentName = decodeIfEncoded(file.parentFile?.name ?: "")
        val grandParentName = decodeIfEncoded(file.parentFile?.parentFile?.name ?: "")
        val greatGrandParentName = decodeIfEncoded(file.parentFile?.parentFile?.parentFile?.name ?: "")

        val seasonPattern2 = "(?:\\[|\\b)([0-9]{1,2})x([0-9]{1,2})(?:\\]|\\b)".toRegex()
        val match2 = seasonPattern2.find(fileName)
            ?: seasonPattern2.find(parentName)
            ?: seasonPattern2.find(grandParentName)
            ?: seasonPattern2.find(greatGrandParentName)

        if (match2 != null) {
            return match2.groupValues[2].toInt()
        }

        val rawEpisodeInfo = findRawEpisodeMatch(file) ?: return null
        val (rawStr, num) = rawEpisodeInfo

        val directSeason = extractDirectSeason(file)
        if (num in 101..9999) {
            if (rawStr.length == 4 && rawStr.startsWith("0")) {
                val s = rawStr.substring(0, 2).toInt()
                val e = rawStr.substring(2, 4).toInt()
                if (directSeason == null || directSeason == s) {
                    return e
                }
            }
            if (!rawStr.startsWith("0") && shouldDisambiguateAsSeasonEpisode(file, directSeason, num, rawStr, mediaItem)) {
                return num % 100
            }
        }

        return num
    }

    private fun findRawEpisodeMatch(file: File): Pair<String, Int>? {
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
        val zeroPaddedSeasonEpisodePattern = "(?<=^|[^a-zA-Z0-9])(0[1-9][0-9]{2})(?=[^a-zA-Z0-9]|$)".toRegex()
        val yearPattern = "\\b([1-2][0-9]{3})\\b".toRegex()
        val seasonEpisodePattern = "(?<=^|[^a-zA-Z])S[0-9]{1,2}[x._\\-\\s]?(?:E|EP)([0-9]{1,3})".toRegex(RegexOption.IGNORE_CASE)
        val yearEpisodePattern = "(?<=^|[^a-zA-Z0-9])(?:19[0-9]{2}|20[0-2][0-9])[eExX]([0-9]{1,3})(?=[^a-zA-Z0-9]|$)".toRegex(RegexOption.IGNORE_CASE)
        val acronymEpisodePattern = "(?<=^|[^a-zA-Z0-9])([a-zA-Z]{1,4})([0-9]{1,3})(?=[^a-zA-Z0-9]|$)".toRegex(RegexOption.IGNORE_CASE)
        val excludedAcronyms = setOf(
            "x264", "h264", "x265", "h265", "hevc", "avc", "xvid", "dvd", "disc", "cd", "bd",
            "part", "pt", "vol", "v", "ch", "aac", "mp3", "mp4", "ac3", "fps", "kbps", "mbps",
            "s", "se", "season", "dvdrip", "bdrip", "brrip", "webrip", "webdl", "hdtv", "pdtv"
        )
        val replaced = fileName.replace(bracketPattern, " ").replace("(?<=[0-9])v[0-9]+".toRegex(), "").replace("_", " ")

        val rawBaseName = fileName.substringBeforeLast(".").replace(bracketPattern, " ").replace("(?<=[0-9])v[0-9]+".toRegex(), "").trim()
        val acronymMatch = acronymEpisodePattern.findAll(rawBaseName).firstOrNull { m ->
            val prefix = m.groupValues[1].lowercase()
            val full = m.value.lowercase()
            !excludedAcronyms.contains(prefix) && !excludedAcronyms.contains(full) && prefix != "s" && prefix != "se"
        }

        val nonEpisodePrefix = "(?i)(?:season|series|disc|disk|cd|dvd|bd|volume|vol|book|chapter|part|pt|day)\\s*$".toRegex()
        val candidatesInOrder = episodePattern.findAll(replaced)
            .map { it to replaced }
            .plus(episodePattern.findAll(fileName).map { it to fileName })
            .filter { (m, text) ->
                val textBefore = text.substring(0, m.range.first).trim()
                !nonEpisodePrefix.containsMatchIn(textBefore)
            }.toList()

        val parentTokens = listOf(parentName, grandParentName, greatGrandParentName)
            .flatMap { it.split(Regex("[^0-9]+")) }
            .filter { it.isNotBlank() }
            .toSet()

        val nonParentCandidates = candidatesInOrder.filter { (m, _) ->
            val numStr = m.groupValues[m.groupValues.size - 1]
            !parentTokens.contains(numStr)
        }

        val firstValidEpisodeMatch = if (nonParentCandidates.isNotEmpty()) {
            nonParentCandidates.first().first
        } else {
            candidatesInOrder.firstOrNull()?.first
        }

        val match = seasonEpisodePattern.find(fileName)
            ?: yearEpisodePattern.find(fileName)
            ?: seasonEpisodePattern.find(parentName)
            ?: yearEpisodePattern.find(parentName)
            ?: seasonEpisodePattern.find(grandParentName)
            ?: seasonEpisodePattern.find(greatGrandParentName)
            ?: explicitEpisodePattern.find(fileName)
            ?: episodeWithTitlePattern.find(fileName)
            ?: episodeWithTitlePattern.find(replaced)
            ?: dashEpisodePattern.find(fileName)
            ?: dashEpisodePattern.find(replaced)
            ?: "-([0-9]{1,3})\\.".toRegex().find(fileName)
            ?: acronymMatch
            ?: zeroPaddedSeasonEpisodePattern.find(fileName)
            ?: standaloneEpisodePattern.find(fileName)?.let { if (yearPattern.find(it.value) == null) it else null }
            ?: firstValidEpisodeMatch
            ?: explicitEpisodePattern.find(parentName)
            ?: explicitEpisodePattern.find(grandParentName)

        if (match != null) {
            val groupIdx = if (match.groupValues.size > 1) match.groupValues.size - 1 else 0
            val str = match.groupValues[groupIdx]
            val num = str.toIntOrNull()
            if (num != null) {
                return Pair(str, num)
            }
        }
        return null
    }

    private fun shouldDisambiguateAsSeasonEpisode(
        file: File,
        knownSeason: Int?,
        num: Int,
        rawStr: String,
        mediaItem: MediaItem? = null
    ): Boolean {
        if (rawStr.startsWith("0")) return false

        // Tier 1: Known season context matching (Folder or path has Season X, Book X, etc.)
        if (knownSeason != null && knownSeason > 0) {
            return (num / 100 == knownSeason) && (num % 100 in 1..99)
        }

        // Tier 2: Sibling file inspection if parent directory exists on disk
        val siblings = file.parentFile?.listFiles()?.filter { it.isFile && it.name != file.name } ?: emptyList()
        if (siblings.isNotEmpty()) {
            val hasZeroPadded3Digits = siblings.any { s ->
                val sName = decodeIfEncoded(s.name)
                Regex("(?<=^|[^0-9])0[0-9]{2}(?=[^0-9]|$)").containsMatchIn(sName)
            }
            if (hasZeroPadded3Digits) return false

            val siblingNumbers = siblings.mapNotNull { s -> findRawEpisodeMatch(s)?.second }
            val hasUnder100 = siblingNumbers.any { it in 1..99 }
            val has100Plus = siblingNumbers.any { it >= 100 } || num >= 100
            if (hasUnder100 && has100Plus) {
                // Continuous episodes from 1 to 100+ -> absolute episode numbering
                return false
            }

            if (siblingNumbers.isNotEmpty() && siblingNumbers.all { it >= 100 } && !hasUnder100) {
                return (num % 100) in 1..99
            }
        }

        // Tier 3 / Tier 4: Metadata verification (TMDB / TVDB) if available (optional)
        if (mediaItem != null && mediaItem.type == MediaType.Series && mediaItem.seasonCount != null) {
            val inferredSeason = num / 100
            if (inferredSeason in 1..(mediaItem.seasonCount ?: 1) && (num % 100) in 1..99) {
                return true
            }
        }

        return false
    }

    fun processFilesFromDirectory(path: String, logFile: File? = null, isTestRun: Boolean = true) {
        val files = File(path).listFiles()
        files?.let { files ->
            for (file in files) {
                if (file.isFile) {
                    val fullName = file.absolutePath
                    // extract Movie or TV show name from full path
                    val extractedName = extractName(file)
                    val year = extractYear(file.absolutePath)
                    var season = extractSeason(file)
                    var episode = extractEpisode(file)

                    val isSeriesPath = file.absolutePath.contains("/Series-Shows/", ignoreCase = true) ||
                            file.absolutePath.contains("\\Series-Shows\\", ignoreCase = true) ||
                            file.absolutePath.contains("/Series/", ignoreCase = true) ||
                            file.absolutePath.contains("/TV/", ignoreCase = true) ||
                            file.absolutePath.contains("/Shows/", ignoreCase = true) ||
                            file.absolutePath.contains("/Anime/", ignoreCase = true) ||
                            file.absolutePath.contains("/Cartoons/", ignoreCase = true)

                    val isMoviePath = file.absolutePath.contains("/Movies/", ignoreCase = true) ||
                            file.absolutePath.contains("\\Movies\\", ignoreCase = true) ||
                            file.absolutePath.contains("/Films/", ignoreCase = true) ||
                            file.absolutePath.contains("/Documentaries/", ignoreCase = true)

                    val mediaIndicator = when {
                        episode != null || season != null -> MediaType.Series
                        isSeriesPath -> MediaType.Series
                        isMoviePath -> MediaType.Movie
                        else -> null
                    }

                    val mediaResult = if (extractedName.isNotBlank()) {
                        val primaryResult = if (year != null) {
                            val res = mediaProxyService.search(extractedName, year, mediaIndicator).firstOrNull()
                            res ?: mediaProxyService.search(extractedName, mediaIndicator).firstOrNull()
                        } else {
                            mediaProxyService.search(extractedName, mediaIndicator).firstOrNull()
                        }

                        if (year != null && (primaryResult == null || (mediaIndicator == MediaType.Series && primaryResult.type != MediaType.Series) || (isSeriesPath && primaryResult.type != MediaType.Series))) {
                            val unstrippedName = "$extractedName $year".trim()
                            val fallbackResult = mediaProxyService.search(unstrippedName, mediaIndicator).firstOrNull()
                            if (fallbackResult != null && (primaryResult == null || fallbackResult.type == MediaType.Series || fallbackResult.title.contains(year.toString()))) {
                                fallbackResult
                            } else {
                                primaryResult
                            }
                        } else {
                            primaryResult
                        }
                    } else {
                        null
                    }

                    if (mediaResult != null) {
                        val refinedEpisode = extractEpisode(file, mediaResult)
                        if (refinedEpisode != null) {
                            episode = refinedEpisode
                        }
                        if (season == null && episode != null && episode > 100 && mediaResult.type == MediaType.Series) {
                            if (mediaResult.seasonCount != null && (episode / 100) <= (mediaResult.seasonCount ?: 1) && (episode % 100) in 1..99) {
                                season = episode / 100
                                episode = episode % 100
                            }
                        }
                    }

                    if (season == null && episode != null && mediaResult?.type == MediaType.Series) {
                        season = 1
                    }

                    val idString = mediaResult?.id ?: ""
                    var logLine =
                        "F:'$fullName'→'$extractedName'→'$mediaResult' (Y: $year, S: $season, E: $episode) $idString"
                    mediaResult?.let {
                        val targetFile = getTargetFile(file, mediaResult, extractedName, season, episode)
                        // actual move
                        // Create the parent directories if they don't exist
                        val newFile = File(targetFile)
                        if(!isTestRun) {
                            newFile.parentFile?.mkdirs()
                            // Then move and rename
                            //file.toPath().moveTo(newFile.toPath(), overwrite = true)
                            //println("T-Moving\n'$file'\n'$newFile'")
                        }
                        logFile?.appendText("T-Moving\n'$file'\n'$newFile'\n")
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
        val homeDir = System.getProperty("user.home")
        val firstPart = homeDir+"/pfdb/PFDB/" + getMediaTypeFolderString(mediaResult) +
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