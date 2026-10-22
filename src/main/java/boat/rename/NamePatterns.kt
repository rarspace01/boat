package boat.rename

/**
 * The regular expressions and word lists the name parsing is made of (see [MediaNameParser], [EpisodeParser]).
 */
object NamePatterns {
    val yearPattern = "\\b(19[0-9]{2}|20[0-2][0-9])\\b".toRegex()

    // placeholders of BitComet like "_____padding_file_0_if you see this file, please update to BitComet 0.85 or above____"
    val paddingFilePattern = "(?i)^_+padding_file_[0-9]+_".toRegex()

    val dvdPattern = "(?<=^|\\s)(?:dvd|disc|cd)\\s?([0-9]{1,2})".toRegex(RegexOption.IGNORE_CASE)

    val bookPattern = "(?<=^|\\s)(?:book|volume|vol|chapter)\\s?([0-9]{1,2})".toRegex(RegexOption.IGNORE_CASE)

    val seasonInNamePattern = "(?<=^|[\\s_.-])(?:Season|Seasons|Series(?=[\\s_.-]*[0-9]{1,2}(?:[^0-9]|$)))[\\s_.-]*(?:S?[0-9]{1,4}|[0-9]{1,2}[-–_~to]+(?:S?[0-9]{1,2})?|S[0-9]{2}[-–_~to]?S?[0-9]{2})?".toRegex(RegexOption.IGNORE_CASE)

    val dayInNamePattern = "(?<=^|[\\s_.-])Day\\s([0-9]{1,2})".toRegex(RegexOption.IGNORE_CASE)

    val packInNamePattern = "(?<=^|[\\s_.-])(?:Complete(?:[\\s_.-]+(?:Series|Season|Seasons|Collection|Set|Pack|Boxset|Box[\\s_.-]+Set|With[\\s_.-]+Subtitles))?|Completo|All[\\s_.-]+(?:Series|Season|Seasons|Episodes|Eps|Collection|Set|Pack|Boxset|Box[\\s_.-]+Set)|Entire[\\s_.-]+(?:Series|Season|Seasons|Collection|Set|Pack|Boxset|Box[\\s_.-]+Set)|Full[\\s_.-]+(?:Series|Season|Seasons|Collection|Set|Pack|Boxset|Box[\\s_.-]+Set)|(?:Boxset|Box[\\s_.-]+Set|Collection|Collector(?:'s|s)?[\\s_.-]+Edition)|With[\\s_.-]+(?:Subtitles|Subs)|Dual[\\s_.-]+Audio|Multi[- ]Audio|Multi[- ]Subs|Tv[\\s_.-]+Series(?:[\\s_.-]+(?:Season|Seasons))?)(?=\\b|[\\s_.-]|$)".toRegex(RegexOption.IGNORE_CASE)

    val extrasInNamePattern = "(?<=^|[\\s_.-])(?:Extras|Extra|Featurettes|Featurette|Bonus|Subs|Subtitles|Gallery|Galleries)(?=\\b|[\\s_.-]|$)".toRegex(RegexOption.IGNORE_CASE)

    val startsWithEpisodeNumberPattern = "^(?:[0-9]{1,3}\\s?[.x-]\\s?[0-9]{1,3}\\s?-\\s?|(?:19[0-9]{2}|20[0-2][0-9])[eExX][0-9]{1,3}[-._\\s]?|S?[0-9]{1,2}[x._\\-\\s]?(?:E|EP)[0-9]{1,3}[-._\\s]?|[0-9]{1,2}x[0-9]{1,2}[-._\\s]?)".toRegex(RegexOption.IGNORE_CASE)

    // "www.Torrenting.org - 90 Day Fiance", "www.Torrenting.org       90 Day Fiance" or "Www.Torrenting.Com.90.Day.Fiance"
    val sitePrefixPattern = "^www[._ ]+[a-z0-9-]+[._ ]+(?:com|org|net|me|to|tv|re|io)(?:\\s*-\\s*|[\\s._]+)".toRegex(RegexOption.IGNORE_CASE)

    val parenthesizedPrefixPattern = "^\\([a-zA-Z0-9.-]+\\)".toRegex()

    val groupPrefixPattern = "^([0-9a-f]{4,10}-)".toRegex(RegexOption.IGNORE_CASE)

    val unitPattern = "^[0-9]+-(?:lb|lb\\.|pound|foot|inch|mile|meter|gram|ton|year|day|hour|min|sec|part|way|ways).*".toRegex()

    val parenthesizedTagsPattern = "(?i)\\((?:[^)]*\\b(?:[0-9]{3,4}p|bluray|blu-ray|dvd|bdrip|dvdrip|web-dl|webdl|webrip|av1|h264|x264|h265|x265|hevc|xvid|divx|dual audio|multi audio|multi subs|proper|repack|sub|subs|dub|dubbed|ger[._ -]?sub|eng[._ -]?sub|moviesbyrizzo|bbc[._ -]*[0-9]{4}|[0-9]{4}\\s*-\\s*[0-9]{2,4}|open matte version|theatrical version|subbed|french|korean|german|japanese|spanish|[0-9a-f]{8})\\b[^)]*)\\)".toRegex()

    val parenthesizedGroupPattern = "\\([^)]*\\)".toRegex()

    val parenthesizedEpisodeRangePattern = "\\([0-9]{1,3}-[0-9]{1,3}\\)".toRegex()

    val episodeRangePattern = "(?<=[a-zA-Z\\s_])[0-9]{1,3}-[0-9]{1,3}(?=[\\s_]*(?:\\+|$|\\b))".toRegex()

    val parenthesizedYearPattern = "[(\\[](?:19[0-9]{2}|20[0-2][0-9])(?:-[0-9]{2,4})?[)\\]]".toRegex()

    // copy counter or cut off word after the year like "Anger.Management.(2011).2" or "Bbc.Planet.Earth.(2006).Se"
    val copyCounterAfterParenthesizedYearPattern = "(?<=[(\\[](?:19|20)[0-9]{2}[)\\]])[._\\s]+(?:[0-9]|[a-zA-Z]{1,2})$".toRegex()

    val yearEpisodePrefixPattern = "^(?:19[0-9]{2}|20[0-2][0-9])[eExX][0-9]{1,3}[-._\\s]?".toRegex(RegexOption.IGNORE_CASE)

    val numericRangePattern = "^[0-9]+(?:[-.][0-9]+)+$".toRegex()

    val explicitEpisodePattern = "(?:\\s-\\s|\\s-_|_-_|\\s-\\s?|_-\\s?|\\s|^)(?:Episode|Ep\\.?|E)\\s*([0-9]{1,3})(?=[^a-zA-Z0-9]|$)".toRegex(RegexOption.IGNORE_CASE)

    val cleanEpisodeMatchPattern = "(?:(?<=^|[^a-zA-Z0-9])(?:Episode|Ep\\.?|E)|(?:\\s-\\s|\\s-_|_-_|\\s-\\s?|_-\\s?|-(?=[0-9])|\\.(?=0[0-9])))([0-9]{1,3})(?=[^a-zA-Z0-9]|$)".toRegex(RegexOption.IGNORE_CASE)

    val zeroPaddedMatchPattern = "(?:\\s|^)0([0-9]{1,3})(?:\\s|$)".toRegex()

    val acronymPattern = "(?<=(?:^|[^a-zA-Z0-9]))(?:[a-zA-Z]\\.){1,}[a-zA-Z](?:\\.(?=[^a-zA-Z0-9]|$)|(?=[^a-zA-Z0-9]|$))".toRegex()

    val extrasPlusPattern = Regex("\\s*\\+\\s*(?:extras|bonus|specials)\\b", RegexOption.IGNORE_CASE)

    // release tags end the title, e.g. "Pride and Prejudice (BBC 1995) Jane Austen 1080p H.264 10GB"
    val midNameTechnicalTagPattern = ("(?i)(?<=[\\s._-])(?:[0-9]{3,4}p|[hx][._ ]?26[45]|bluray|blu-ray|bdrip|brrip|dvdrip|" +
            "dvd-?rip|web-?dl|webrip|hdtv|xvid|hevc|bd-?box|dvd-?box|mini-?series)(?=[\\s._\\-\\])]|$)").toRegex()

    val trailingQualityTag = "(?i)\\s+(?:720p|1080p|1080i|2160p|480p|4k|uhd|fhd|hdtv|bluray|blu-ray|bdrip|dvdrip|dvd|web-dl|webdl|webrip|web|remux|proper|repack|rerip|x264|x265|h264|h265|hevc|xvid|dual audio|multi audio|multi subs|subbed|korean|french|german|japanese|spanish|english|director'?s cut|extended cut|uncensored|remastered|tv series|season|seasons|complete series|complete season|all seasons|mini-series|mini series|miniseries|remastered mini series)$".toRegex()

    val trailingYearPattern = "(?<=[a-zA-Z\\s])\\s+(?:19[0-9]{2}|20[0-2][0-9])(?:-[0-9]{2,4})?$".toRegex()

    // "Catch-22 2019", only after cutting at the season, a remaining "-22" would be read as episode otherwise
    val trailingYearAfterNumberPattern = "(?<=[0-9])\\s+(?:19[0-9]{2}|20[0-2][0-9])$|(?<=[a-zA-Z])-(?:19[0-9]{2}|20[0-2][0-9])$".toRegex()

    val trailingEpisodeMarkerPattern = Regex("(?i)(?:\\s*-\\s*|\\s+)(?:Episode|Ep|E)\\.?$")

    // left over from cutting "Mr. Bean - The Complete Collection" or "Lost The Complete Series"
    val trailingDashArticlePattern = Regex("(?i)\\s+-\\s+(?:the|a|an)$|(?<=\\S)\\s+the$")

    val trailingEpisodePattern = "(?<=[a-zA-Z\\s])-(?:ep)?([0-9]{1,3})$".toRegex(RegexOption.IGNORE_CASE)

    val multiNumberTrailingPattern = "(?<=[0-9])\\s+([0-9]{1,3})$".toRegex()

    // folders holding extras or subtitles, everything below them describes the extra and not the show,
    // e.g. "Bonus Features/Season 1/Lost Revealed", "Featurettes/Season 3/Menu Art", "subtitles/english"
    val extrasFolderPattern = ("(?i)^(?:(?:season|series)\\s*[0-9]+\\s+)?(?:dvd\\s+|bd\\s+|bonus\\s+|special\\s+)?" +
            "(?:extras|extra|featurettes|featurette|bonus|bonus features|special features)$").toRegex()

    val subtitleFolderPattern = "(?i)^(?:english\\s+|eng\\s+)?(?:subtitles|subtitle|subs)(?:\\s+folder)?$".toRegex()

    val subtitleFlags = setOf("sdh", "forced", "cc")

    val trailingSeasonNumberPattern = "(?i)(?<=[a-z])(?:\\s+-)?(?:\\s+(?:series|season))?\\s+([0-9]{1,2})$".toRegex()

    val rangeSinglePattern = "^[0-9]-[0-9]$".toRegex()

    val rangeLetterPattern = "^[a-z]-[a-z]$".toRegex()

    val pureNumberPattern = "^[0-9]+$".toRegex()

    val numberedSubFolderPattern = "^[0-9]{1,2}[a-z]$".toRegex()

    val hexHashPattern = "^[0-9a-f]{8,}$".toRegex()

    val seasonDiscPartPattern = "^(?:uk|us|usa|ger|fra|fr|de|es|ita|it|jp|jpn|kr|kor|cn|chi|ru|rus|eng|en)?\\s*(?:season|series|disc|disk|cd|dvd|bd|volume|vol|book|chapter|part|ep|episode|day)\\s*[0-9]+.*$".toRegex()

    val sEPattern = "^s[0-9]{1,2}(?:\\s?e[0-9]{1,2})?$".toRegex()

    val xPattern = "^[0-9]{1,2}\\s?x\\s?[0-9]{1,2}$".toRegex()

    val dashDotNumPattern = "^[0-9]{1,3}\\s?[.\\sx-]\\s?[0-9]{1,3}$".toRegex()

    // markers like "Vol 2", "Ep 12" or "Teil 1", but titles like "Area 88" aren't junk
    val shortWordNumPattern = "^(?:[a-z]{1,3}|teil|band)\\s?[0-9]{1,3}$".toRegex()

    val numShortWordPattern = "^[0-9]+\\s?[a-z]{2,3}$".toRegex()

    val nonAlphanumericOnlyPattern = Regex("[^a-z0-9]")

    val whitespacePattern = Regex("\\s+")

    val packWithExtrasPattern = ".*\\b(?:season|s[0-9]+|[0-9]+-[0-9]+)\\s*(?:\\+|plus|with|and)\\s*(?:extras|bonus).*".toRegex()

    val shortAcronymPattern = "^[a-zA-Z]{2,6}$".toRegex()

    val episodeNumberWithTitlePattern = "^(.+?)\\s+(?:-\\s+)?([0-9]{2,4})(?:\\s+-)?\\s+\\S".toRegex()

    val leadingTrackNumberPattern = "^[0-9]{1,3}(?:-[0-9]{1,3})?(?:[.\\s_]+|\\s*-\\s*)(?=[a-zA-Z])".toRegex()

    val remainderEpisodePattern = "^(?:[-_\\s]|ep|episode|e)*[0-9]{1,4}$".toRegex(RegexOption.IGNORE_CASE)

    val remainderYearOrNumPattern = "^(?:19[0-9]{2}|20[0-2][0-9]|[0-9]{1,4})$".toRegex()

    val parenthesizedYearExtractorRegex = "[(\\[](19[0-9]{2}|20[0-2][0-9])(?:-[0-9]{2,4})?[)\\]]".toRegex()

    val yearExtractorRegex = "(?<=^|[^0-9])(19[0-9]{2}|20[0-2][0-9])(?=[^0-9]|$)(?![xX][0-9])".toRegex()

    val seasonFolderPattern = "(?i)^(?:uk|us|usa|ger|fra|fr|de|es|ita|it|jp|jpn|kr|kor|cn|chi|ru|rus|eng|en)?[\\s_.-]*(?:Season|Series|Specials|Dis[ck]|DVD|CD|Vol(?:ume)?)(?:[\\s_.-]*[0-9]{1,4}|\\b)".toRegex()

    // movies in a series folder, e.g. "Naruto/3.Naruto Movies/1 - Ninja Clash in the Land of Snow.mkv" or "Show/Movie (2005)/"
    val movieFolderWordPattern = "(?i)(?<![a-z])(?:movies?|films?)(?![a-z])".toRegex()

    // only "movie" in file names, "Film Versions" or "Short Film" are episodes
    val movieFileWordPattern = "(?i)(?<![a-z])movie(?![a-z])".toRegex()

    // packs with episodes and movies like "Eden of the East 1-11+Movie 1-2 Complete" or "AEON FLUX - TV Series and 2005 Movie"
    val episodesAndMoviesPackPattern = "(?i)\\+|&|(?<![a-z])(?:and|complete|series|episodes)(?![a-z])".toRegex()

    val pilotPattern = "(?i)(?<![a-z])pilot(?![a-z])".toRegex()

    val movieNumberPattern = "(?i)(?<![a-z])(?:(?:the\\s+)?movie\\s*[0-9]{1,2}|the\\s+movie)(?![a-z0-9])|\\s-\\s*movie(?=\\s*(?:-|$))".toRegex()

    // the movie's own title after its number, e.g. "Ranma ½ [1991] - Movie 01 - Big Trouble In Nekonron China",
    // "Hokuto no Ken - Movie 2006 - The Legend of Raoh" or "Trigun Movie Badlands Rumble"
    val movieSubtitlePattern = "(?i)^(.*?)(?<![a-z])movie(?:\\s*(?:[0-9]{1,2}|19[0-9]{2}|20[0-2][0-9]))?\\s*(?:-\\s*)?([a-z].+)$".toRegex()

    val repeatedDashPattern = "\\s*-\\s*(?:-\\s*)+".toRegex()

    // "Captain.Future.Folge.13-19" or "Eden.of.the.East.Episode.10" before the dots become spaces
    val episodeWordPattern = "(?i)(?<=[\\s._-])(?:Folge|Episode)(?=[\\s._-]*[0-9])".toRegex()

    // the show after the episode, e.g. "S18E11-HAK5-Open Source Distributed Decentralized Social Networks"
    val showAfterEpisodeMarkerPattern = "(?i)^S[0-9]{1,2}E[0-9]{1,3}[\\s._]*-[\\s._]*([^-]+?)[\\s._]*-".toRegex()

    // junk next to the media like documents, images, links, checksums or downloads in progress
    val nonMediaExtensions = setOf(
        "pdf", "png", "jpg", "jpeg", "bmp", "gif", "ico", "txt", "nfo", "doc", "docx", "xls", "xlsx", "rtf", "html", "htm", "xml",
        "ttf", "otf", "website", "url", "md5", "sfv", "par2", "!qb", "part", "torrent", "exe", "ini", "db", "ds_store"
    )

    // parts of a mini series like "The Shining - Part 1 Stephen King Mini-Series"
    val partInNamePattern = "(?i)(?:\\s+-)?\\s+Part\\s*[0-9]{1,2}(?=[^0-9a-z]|$)".toRegex()

    val yearEpisodeInNamePattern = "(?<=[\\s_.-])(?:19[0-9]{2}|20[0-2][0-9])[eExX][0-9]{1,3}(?:[xX][0-9]{1,3})?(?=[^a-zA-Z0-9]|$)".toRegex()

    val remoteMediaIdPattern = "\\{(?:tmdb|tvdb)-[0-9]+}".toRegex(RegexOption.IGNORE_CASE)

    val releaseGroups: Set<String> = NamePatterns::class.java.getResourceAsStream("/releasegroups.txt")
        ?.bufferedReader()
        ?.useLines { lines -> lines.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.map { it.lowercase() }.toSet() }
        ?: emptySet()

    val releaseGroupAlternatives = releaseGroups.joinToString("|") { Regex.escape(it) }

    val leadingReleaseGroupPattern = "^(?:$releaseGroupAlternatives)-(?=.)".toRegex(RegexOption.IGNORE_CASE)

    val trailingReleaseGroupPattern = "(?<=.)-(?:$releaseGroupAlternatives)$".toRegex(RegexOption.IGNORE_CASE)

    // ISO 639-1 and 639-2/T codes, plus the 639-2/B variants commonly used for subtitles (e.g. ger instead of deu)
    // English language names to their code, e.g. "danish" to "da"
    val languageNames = java.util.Locale.getISOLanguages().associateBy { java.util.Locale.of(it).getDisplayLanguage(java.util.Locale.ENGLISH).lowercase() }

    val nonLetterPattern = "[^a-z]+".toRegex()

    val categoryFolderNames = setOf(
        "series-shows", "series shows", "series", "shows", "tv", "tv shows", "tv show", "tv-shows",
        "tv series", "tv-series", "movies", "movie", "films", "film", "anime",
        "cartoons", "cartoon", "documentaries", "documentary", "downloads", "download",
        "torrents", "torrent", "complete", "incomplete", "pfdb", "data", "videos",
        "video", "media", "home", "root", "desktop"
    )

    // movie name parsing
    val movieYearPattern = "^(?:19[0-9]{2}|20[0-2][0-9])$".toRegex()

    val movieTechnicalTagPattern = ("^(?:[0-9]{3,4}[pi]|[248]k|uhd|ultrahd|fhd|hdr(?:10)?(?:plus)?|dovi|10bit|8bit|" +
            "(?:b[dr]|hd|dvd|vhs|web|fhd|tv|hdtv|sat|dvb)rip(?:[0-9]{3,4}[pi])?|(?:hd|dvd|bd)mux|bluray|blu-ray|bdremux|remux|dvd[59r]?|dvdscr|webdl|web-dl|hdtv|hdcam|screener|ntsc|" +
            "x26[45]|h26[45]|hevc|avc|xvid|divx|av1|aac[0-9]*|e?ac3|dts(?:hd)?(?:ma)?|truehd|atmos|ddp[0-9]*|dd[0-9]+|flac|" +
            "amzn|hmax|dsnp|atvp|hulu|itunes|" +
            "mkv|mp4|avi|[0-9]+(?:[.,][0-9]+)?[mg]b|[0-9]{3,4}x[0-9]{3,4})$").toRegex()

    val movieLanguageOrEditionTagPattern = ("^(?:german|deutsch|ger|dl|eng|ita|fre|fra|vf|vff|vostfr|truefrench|spa|esp|castellano|latino|rus|ukr|kor|jap|jpn|hindi|chs|cht|" +
            "multi|multisubs?|dual|dubbed|subbed|ensubbed|esubs?|omu|nlsubs?|hardsubs?|subs?|" +
            "unrated|uncut|remastered|extended|internal|proper|repack|rerip|limited|criterion|imax|rm4k|theatrical|retail|dircut)$").toRegex()

    val trailingMovieTagPattern = ("(?i)\\s+(?:german|deutsch|ger|dl|eng|ita|multisubs?|dubbed|subbed|" +
            "unrated|uncut|remastered|extended|internal|proper|repack|limited|criterion|imax|rm4k|theatrical|retail|dircut|" +
            "directors?'?s?[\\s-]+cut|(?:extended|theatrical|unrated)[\\s-]+(?:cut|edition|version)|uncut[\\s-]+version|" +
            "(?:special|collectors?'?s?)[\\s-]+edition|(?:with|plus)[\\s-]+extras)$").toRegex()

    // torrent hash and "title=" of magnet-like names, or the "Eto." (Russian "this is") of kinozal releases
    val movieHashPrefixPattern = "(?i)^(?:[0-9a-f]{32,40}\\s*(?:title=)?|eto[\\s.]+(?=\\S))".toRegex()

    // "Blade.3.Trinity.20041080p" glues the year to the resolution
    val yearGluedToResolutionPattern = "(?i)(?<=[^0-9])((?:19[0-9]{2}|20[0-2][0-9]))(?=(?:2160|1080|720|576|480)[pi])".toRegex()

    val movieSitePrefixPattern = "(?i)^www\\.\\S+?\\.(?:com|org|net|me|to|tv|re|io|cc|ws)\\s*-?\\s+".toRegex()

    val leadingBracketGroupsPattern = "^(?:\\s*\\[[^\\]]*])+".toRegex()

    // list numbering of collections like "01. The Big Boss" or "a. Bruce", not titles like "12.Angry.Men"
    val listNumberPrefixPattern = "^(?:[0-9]{1,3}[a-z]?|[a-z])\\.\\s+".toRegex(RegexOption.IGNORE_CASE)

    val tokenPattern = "\\S+".toRegex()

    val yearRangePattern = "(19[0-9]{2}|20[0-2][0-9])\\s*-\\s*(19[0-9]{2}|20[0-2][0-9]|[0-9]{2})(?![0-9])".toRegex()

    val leadingYearTitlePattern = "^(19[0-9]{2}|20[0-2][0-9])\\s+(.*[a-zA-Z].*)$".toRegex()

    val episodeLikeShortPattern = "^(?:e|s|se|pt|x)\\s?[0-9]+$".toRegex()

    // series detection in the movies folder
    val seasonEpisodeMarkerPattern = "(?i)(?<![a-z0-9])S[0-9]{1,2}[\\s._-]?E[0-9]{1,3}(?![0-9])".toRegex()

    // 5x10, but not audio tags like 2.0x2 or resolutions like 1920x1080p
    val seasonXEpisodeMarkerPattern = "(?<![0-9a-zA-Z.])(?:[1-9]|[0-9]{2})x[0-9]{2}(?![0-9a-zA-Z])".toRegex()

    val trailingEpisodeNumberMarkerPattern = "\\s([0-9]{2,3})$".toRegex()

    val seasonFolderMarkerPattern = "(?i)(?<![a-z0-9])(?:S[0-9]{1,2}|Season[\\s._-]*[0-9]{1,2})(?![0-9])".toRegex()

    // files that aren't renamed at all
    val creditlessFolderPattern = "(?i)^(?:nc|ncop|nced|ncop\\s*&\\s*nced|creditless(?:[\\s_.-].*)?)$".toRegex()

    val discStructureRootNames = setOf("bdmv", "video_ts", "audio_ts", "certificate", "aacs")

    val discStructureFolderNames = discStructureRootNames + setOf("stream", "playlist", "clipinf", "backup", "bdjo", "meta", "jar")

    val coursePattern = "(?i)udemy|cbt[\\s._-]?nuggets?|freecoursesonline|masterclass|devcourseweb|lynda|pluralsight|coursera|skillshare|linkedin[\\s._-]learning".toRegex()
}
