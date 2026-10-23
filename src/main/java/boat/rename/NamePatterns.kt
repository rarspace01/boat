package boat.rename

// The regular expressions and word lists the name parsing is made of (see MediaNameParser, EpisodeParser).

internal val yearPattern = "\\b(19[0-9]{2}|20[0-2][0-9])\\b".toRegex()

// placeholders of BitComet like "_____padding_file_0_if you see this file, please update to BitComet 0.85 or above____"
internal val paddingFilePattern = "(?i)^_+padding_file_[0-9]+_".toRegex()

internal val dvdPattern = "(?<=^|\\s)(?:dvd|disc|cd)\\s?([0-9]{1,2})".toRegex(RegexOption.IGNORE_CASE)

internal val bookPattern = "(?<=^|\\s)(?:book|volume|vol|chapter)\\s?([0-9]{1,2})".toRegex(RegexOption.IGNORE_CASE)

internal val seasonInNamePattern = "(?<=^|[\\s_.-])(?:Season|Seasons|Series(?=[\\s_.-]*[0-9]{1,2}(?:[^0-9]|$)))[\\s_.-]*(?:S?[0-9]{1,4}|[0-9]{1,2}[-–_~to]+(?:S?[0-9]{1,2})?|S[0-9]{2}[-–_~to]?S?[0-9]{2})?".toRegex(RegexOption.IGNORE_CASE)

internal val dayInNamePattern = "(?<=^|[\\s_.-])Day\\s([0-9]{1,2})".toRegex(RegexOption.IGNORE_CASE)

internal val packInNamePattern = "(?<=^|[\\s_.-])(?:Complete(?:[\\s_.-]+(?:Series|Season|Seasons|Collection|Set|Pack|Boxset|Box[\\s_.-]+Set|With[\\s_.-]+Subtitles))?|Completo|All[\\s_.-]+(?:Series|Season|Seasons|Episodes|Eps|Collection|Set|Pack|Boxset|Box[\\s_.-]+Set)|Entire[\\s_.-]+(?:Series|Season|Seasons|Collection|Set|Pack|Boxset|Box[\\s_.-]+Set)|Full[\\s_.-]+(?:Series|Season|Seasons|Collection|Set|Pack|Boxset|Box[\\s_.-]+Set)|(?:Boxset|Box[\\s_.-]+Set|Collection|Collector(?:'s|s)?[\\s_.-]+Edition)|With[\\s_.-]+(?:Subtitles|Subs)|Dual[\\s_.-]+Audio|Multi[- ]Audio|Multi[- ]Subs|Tv[\\s_.-]+Series(?:[\\s_.-]+(?:Season|Seasons))?)(?=\\b|[\\s_.-]|$)".toRegex(RegexOption.IGNORE_CASE)

internal val extrasInNamePattern = "(?<=^|[\\s_.-])(?:Extras|Extra|Featurettes|Featurette|Bonus|Subs|Subtitles|Gallery|Galleries)(?=\\b|[\\s_.-]|$)".toRegex(RegexOption.IGNORE_CASE)

internal val startsWithEpisodeNumberPattern = "^(?:[0-9]{1,3}\\s?[.x-]\\s?[0-9]{1,3}\\s?-\\s?|(?:19[0-9]{2}|20[0-2][0-9])[eExX][0-9]{1,3}[-._\\s]?|S?[0-9]{1,2}[x._\\-\\s]?(?:E|EP)[0-9]{1,3}[-._\\s]?|[0-9]{1,2}x[0-9]{1,2}[-._\\s]?)".toRegex(RegexOption.IGNORE_CASE)

// "www.Torrenting.org - 90 Dar Fafelu", "www.Torrenting.org       90 Dar Fafelu" or "Www.Torrenting.Com.90.Dar.Fafelu"
internal val sitePrefixPattern = "^www[._ ]+[a-z0-9-]+[._ ]+(?:com|org|net|me|to|tv|re|io)(?:\\s*-\\s*|[\\s._]+)".toRegex(RegexOption.IGNORE_CASE)

internal val parenthesizedPrefixPattern = "^\\([a-zA-Z0-9.-]+\\)".toRegex()

internal val groupPrefixPattern = "^([0-9a-f]{4,10}-)".toRegex(RegexOption.IGNORE_CASE)

internal val unitPattern = "^[0-9]+-(?:lb|lb\\.|pound|foot|inch|mile|meter|gram|ton|year|day|hour|min|sec|part|way|ways).*".toRegex()

internal val parenthesizedTagsPattern = "(?i)\\((?:[^)]*\\b(?:[0-9]{3,4}p|bluray|blu-ray|dvd|bdrip|dvdrip|web-dl|webdl|webrip|av1|h264|x264|h265|x265|hevc|xvid|divx|dual audio|multi audio|multi subs|proper|repack|sub|subs|dub|dubbed|ger[._ -]?sub|eng[._ -]?sub|moviesbyrizzo|bbc[._ -]*[0-9]{4}|[0-9]{4}\\s*-\\s*[0-9]{2,4}|open matte version|theatrical version|subbed|french|korean|german|japanese|spanish|[0-9a-f]{8})\\b[^)]*)\\)".toRegex()

internal val parenthesizedGroupPattern = "\\([^)]*\\)".toRegex()

internal val parenthesizedEpisodeRangePattern = "\\([0-9]{1,3}-[0-9]{1,3}\\)".toRegex()

internal val episodeRangePattern = "(?<=[a-zA-Z\\s_])[0-9]{1,3}-[0-9]{1,3}(?=[\\s_]*(?:\\+|$|\\b))".toRegex()

internal val parenthesizedYearPattern = "[(\\[](?:19[0-9]{2}|20[0-2][0-9])(?:-[0-9]{2,4})?[)\\]]".toRegex()

// copy counter or cut off word after the year like "Asubu.Masokagupi.(2011).2" or "Bbc.Pipadu.Eseve.(2006).Se"
internal val copyCounterAfterParenthesizedYearPattern = "(?<=[(\\[](?:19|20)[0-9]{2}[)\\]])[._\\s]+(?:[0-9]|[a-zA-Z]{1,2})$".toRegex()

internal val yearEpisodePrefixPattern = "^(?:19[0-9]{2}|20[0-2][0-9])[eExX][0-9]{1,3}[-._\\s]?".toRegex(RegexOption.IGNORE_CASE)

internal val numericRangePattern = "^[0-9]+(?:[-.][0-9]+)+$".toRegex()

internal val explicitEpisodePattern = "(?:\\s-\\s|\\s-_|_-_|\\s-\\s?|_-\\s?|\\s|^)(?:Episode|Ep\\.?|E)\\s*([0-9]{1,3})(?=[^a-zA-Z0-9]|$)".toRegex(RegexOption.IGNORE_CASE)

internal val cleanEpisodeMatchPattern = "(?:(?<=^|[^a-zA-Z0-9])(?:Episode|Ep\\.?|E)|(?:\\s-\\s|\\s-_|_-_|\\s-\\s?|_-\\s?|-(?=[0-9])|\\.(?=0[0-9])))([0-9]{1,3})(?=[^a-zA-Z0-9]|$)".toRegex(RegexOption.IGNORE_CASE)

internal val zeroPaddedMatchPattern = "(?:\\s|^)0([0-9]{1,3})(?:\\s|$)".toRegex()

internal val acronymPattern = "(?<=(?:^|[^a-zA-Z0-9]))(?:[a-zA-Z]\\.){1,}[a-zA-Z](?:\\.(?=[^a-zA-Z0-9]|$)|(?=[^a-zA-Z0-9]|$))".toRegex()

internal val extrasPlusPattern = Regex("\\s*\\+\\s*(?:extras|bonus|specials)\\b", RegexOption.IGNORE_CASE)

// release tags end the title, e.g. "Pumek and Pipirebak (BBC 1995) Jane Austen 1080p H.264 10GB"
internal val midNameTechnicalTagPattern = ("(?i)(?<=[\\s._-])(?:[0-9]{3,4}p|[hx][._ ]?26[45]|bluray|blu-ray|bdrip|brrip|dvdrip|" +
        "dvd-?rip|web-?dl|webrip|hdtv|xvid|hevc|bd-?box|dvd-?box|mini-?series)(?=[\\s._\\-\\])]|$)").toRegex()

internal val trailingQualityTag = "(?i)\\s+(?:720p|1080p|1080i|2160p|480p|4k|uhd|fhd|hdtv|bluray|blu-ray|bdrip|dvdrip|dvd|web-dl|webdl|webrip|web|remux|proper|repack|rerip|x264|x265|h264|h265|hevc|xvid|dual audio|multi audio|multi subs|subbed|korean|french|german|japanese|spanish|english|director'?s cut|extended cut|uncensored|remastered|tv series|season|seasons|complete series|complete season|all seasons|mini-series|mini series|miniseries|remastered mini series)$".toRegex()

internal val trailingYearPattern = "(?<=[a-zA-Z\\s])\\s+(?:19[0-9]{2}|20[0-2][0-9])(?:-[0-9]{2,4})?$".toRegex()

// "Coniz-22 2019", only after cutting at the season, a remaining "-22" would be read as episode otherwise
internal val trailingYearAfterNumberPattern = "(?<=[0-9])\\s+(?:19[0-9]{2}|20[0-2][0-9])$|(?<=[a-zA-Z])-(?:19[0-9]{2}|20[0-2][0-9])$".toRegex()

internal val trailingEpisodeMarkerPattern = Regex("(?i)(?:\\s*-\\s*|\\s+)(?:Episode|Ep|E)\\.?$")

// left over from cutting "Mr. Buki - The Complete Collection" or "Lecu The Complete Series"
internal val trailingDashArticlePattern = Regex("(?i)\\s+-\\s+(?:the|a|an)$|(?<=\\S)\\s+the$")

internal val trailingEpisodePattern = "(?<=[a-zA-Z\\s])-(?:ep)?([0-9]{1,3})$".toRegex(RegexOption.IGNORE_CASE)

internal val multiNumberTrailingPattern = "(?<=[0-9])\\s+([0-9]{1,3})$".toRegex()

// folders holding extras or subtitles, everything below them describes the extra and not the show,
// e.g. "Bekem Favobevo/Season 1/Lecu Revealed", "Featurettes/Season 3/Menu Art", "subtitles/english"
internal val extrasFolderPattern = ("(?i)^(?:(?:season|series)\\s*[0-9]+\\s+)?(?:dvd\\s+|bd\\s+|bonus\\s+|special\\s+)?" +
        "(?:extras|extra|featurettes|featurette|bonus|bekem favobevo|special features)$").toRegex()

internal val subtitleFolderPattern = "(?i)^(?:english\\s+|eng\\s+)?(?:subtitles|subtitle|subs)(?:\\s+folder)?$".toRegex()

internal val subtitleFlags = setOf("sdh", "forced", "cc")

internal val trailingSeasonNumberPattern = "(?i)(?<=[a-z])(?:\\s+-)?(?:\\s+(?:series|season))?\\s+([0-9]{1,2})$".toRegex()

internal val rangeSinglePattern = "^[0-9]-[0-9]$".toRegex()

internal val rangeLetterPattern = "^[a-z]-[a-z]$".toRegex()

internal val pureNumberPattern = "^[0-9]+$".toRegex()

internal val numberedSubFolderPattern = "^[0-9]{1,2}[a-z]$".toRegex()

internal val hexHashPattern = "^[0-9a-f]{8,}$".toRegex()

internal val seasonDiscPartPattern = "^(?:uk|us|usa|ger|fra|fr|de|es|ita|it|jp|jpn|kr|kor|cn|chi|ru|rus|eng|en)?\\s*(?:season|series|disc|disk|cd|dvd|bd|volume|vol|book|chapter|part|ep|episode|day)\\s*[0-9]+.*$".toRegex()

internal val sEPattern = "^s[0-9]{1,2}(?:\\s?e[0-9]{1,2})?$".toRegex()

internal val xPattern = "^[0-9]{1,2}\\s?x\\s?[0-9]{1,2}$".toRegex()

internal val dashDotNumPattern = "^[0-9]{1,3}\\s?[.\\sx-]\\s?[0-9]{1,3}$".toRegex()

// markers like "Vol 2", "Ep 12" or "Teil 1", but titles like "Atel 88" aren't junk
internal val shortWordNumPattern = "^(?:[a-z]{1,3}|teil|band)\\s?[0-9]{1,3}$".toRegex()

internal val numShortWordPattern = "^[0-9]+\\s?[a-z]{2,3}$".toRegex()

internal val nonAlphanumericOnlyPattern = Regex("[^a-z0-9]")

internal val whitespacePattern = Regex("\\s+")

internal val packWithExtrasPattern = ".*\\b(?:season|s[0-9]+|[0-9]+-[0-9]+)\\s*(?:\\+|plus|with|and)\\s*(?:extras|bonus).*".toRegex()

internal val shortAcronymPattern = "^[a-zA-Z]{2,6}$".toRegex()

internal val episodeNumberWithTitlePattern = "^(.+?)\\s+(?:-\\s+)?([0-9]{2,4})(?:\\s+-)?\\s+\\S".toRegex()

internal val leadingTrackNumberPattern = "^[0-9]{1,3}(?:-[0-9]{1,3})?(?:[.\\s_]+|\\s*-\\s*)(?=[a-zA-Z])".toRegex()

internal val remainderEpisodePattern = "^(?:[-_\\s]|ep|episode|e)*[0-9]{1,4}$".toRegex(RegexOption.IGNORE_CASE)

internal val remainderYearOrNumPattern = "^(?:19[0-9]{2}|20[0-2][0-9]|[0-9]{1,4})$".toRegex()

internal val parenthesizedYearExtractorRegex = "[(\\[](19[0-9]{2}|20[0-2][0-9])(?:-[0-9]{2,4})?[)\\]]".toRegex()

internal val yearExtractorRegex = "(?<=^|[^0-9])(19[0-9]{2}|20[0-2][0-9])(?=[^0-9]|$)(?![xX][0-9])".toRegex()

internal val seasonFolderPattern = "(?i)^(?:uk|us|usa|ger|fra|fr|de|es|ita|it|jp|jpn|kr|kor|cn|chi|ru|rus|eng|en)?[\\s_.-]*(?:Season|Series|Specials|Dis[ck]|DVD|CD|Vol(?:ume)?)(?:[\\s_.-]*[0-9]{1,4}|\\b)".toRegex()

// movies in a series folder, e.g. "Nikulo/3.Nikulo Movies/1 - Noget Cakez in the Lufu of Seme.mkv" or "Show/Movie (2005)/"
internal val movieFolderWordPattern = "(?i)(?<![a-z])(?:movies?|films?)(?![a-z])".toRegex()

// only "movie" in file names, "Film Versions" or "Short Film" are episodes
internal val movieFileWordPattern = "(?i)(?<![a-z])movie(?![a-z])".toRegex()

// packs with episodes and movies like "Ecuf of the Edaz 1-11+Movie 1-2 Complete" or "ASUG FABE - TV Series and 2005 Movie"
internal val episodesAndMoviesPackPattern = "(?i)\\+|&|(?<![a-z])(?:and|complete|series|episodes)(?![a-z])".toRegex()

internal val pilotPattern = "(?i)(?<![a-z])pilot(?![a-z])".toRegex()

internal val movieNumberPattern = "(?i)(?<![a-z])(?:(?:the\\s+)?movie\\s*[0-9]{1,2}|the\\s+movie)(?![a-z0-9])|\\s-\\s*movie(?=\\s*(?:-|$))".toRegex()

// the movie's own title after its number, e.g. "Ruvem ½ [1991] - Movie 01 - Big Trouble In Nekonron China",
// "Hcates no Kes - Movie 2006 - The Lazeru of Ruri" or "Tatene Movie Badlands Rumble"
internal val movieSubtitlePattern = "(?i)^(.*?)(?<![a-z])movie(?:\\s*(?:[0-9]{1,2}|19[0-9]{2}|20[0-2][0-9]))?\\s*(?:-\\s*)?([a-z].+)$".toRegex()

internal val repeatedDashPattern = "\\s*-\\s*(?:-\\s*)+".toRegex()

// "Cutecip.Fipusu.Folge.13-19" or "Ecuf.of.the.Edaz.Episode.10" before the dots become spaces
internal val episodeWordPattern = "(?i)(?<=[\\s._-])(?:Folge|Episode)(?=[\\s._-]*[0-9])".toRegex()

// the show after the episode, e.g. "S18E11-HAK5-Open Source Distributed Decentralized Social Networks"
internal val showAfterEpisodeMarkerPattern = "(?i)^S[0-9]{1,2}E[0-9]{1,3}[\\s._]*-[\\s._]*([^-]+?)[\\s._]*-".toRegex()

// junk next to the media like documents, images, links, checksums or downloads in progress
internal val nonMediaExtensions = setOf(
    "pdf", "png", "jpg", "jpeg", "bmp", "gif", "ico", "txt", "nfo", "doc", "docx", "xls", "xlsx", "rtf", "html", "htm", "xml",
    "ttf", "otf", "website", "url", "md5", "sfv", "par2", "!qb", "part", "torrent", "exe", "ini", "db", "ds_store"
)

// parts of a mini series like "The Sukukid - Part 1 Stephen King Mini-Series"
internal val partInNamePattern = "(?i)(?:\\s+-)?\\s+Part\\s*[0-9]{1,2}(?=[^0-9a-z]|$)".toRegex()

internal val yearEpisodeInNamePattern = "(?<=[\\s_.-])(?:19[0-9]{2}|20[0-2][0-9])[eExX][0-9]{1,3}(?:[xX][0-9]{1,3})?(?=[^a-zA-Z0-9]|$)".toRegex()

internal val remoteMediaIdPattern = "\\{(?:tmdb|tvdb)-[0-9]+}".toRegex(RegexOption.IGNORE_CASE)

internal val releaseGroups: Set<String> = MediaNameParser::class.java.getResourceAsStream("/releasegroups.txt")
    ?.bufferedReader()
    ?.useLines { lines -> lines.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.map { it.lowercase() }.toSet() }
    ?: emptySet()

internal val releaseGroupAlternatives = releaseGroups.joinToString("|") { Regex.escape(it) }

internal val leadingReleaseGroupPattern = "^(?:$releaseGroupAlternatives)-(?=.)".toRegex(RegexOption.IGNORE_CASE)

internal val trailingReleaseGroupPattern = "(?<=.)-(?:$releaseGroupAlternatives)$".toRegex(RegexOption.IGNORE_CASE)

// ISO 639-1 and 639-2/T codes, plus the 639-2/B variants commonly used for subtitles (e.g. ger instead of deu)
// English language names to their code, e.g. "danish" to "da"
internal val languageNames = java.util.Locale.getISOLanguages().associateBy { java.util.Locale.of(it).getDisplayLanguage(java.util.Locale.ENGLISH).lowercase() }

internal val nonLetterPattern = "[^a-z]+".toRegex()

internal val categoryFolderNames = setOf(
    "series-shows", "series shows", "series", "shows", "tv", "tv shows", "tv show", "tv-shows",
    "tv series", "tv-series", "movies", "movie", "films", "film", "anime",
    "cartoons", "cartoon", "documentaries", "documentary", "downloads", "download",
    "torrents", "torrent", "complete", "incomplete", "pfdb", "data", "videos",
    "video", "media", "home", "root", "desktop"
)

// movie name parsing
internal val movieYearPattern = "^(?:19[0-9]{2}|20[0-2][0-9])$".toRegex()

internal val movieTechnicalTagPattern = ("^(?:[0-9]{3,4}[pi]|[248]k|uhd|ultrahd|fhd|hdr(?:10)?(?:plus)?|dovi|10bit|8bit|" +
        "(?:b[dr]|hd|dvd|vhs|web|fhd|tv|hdtv|sat|dvb)rip(?:[0-9]{3,4}[pi])?|(?:hd|dvd|bd)mux|bluray|blu-ray|bdremux|remux|dvd[59r]?|dvdscr|webdl|web-dl|hdtv|hdcam|screener|ntsc|" +
        "x26[45]|h26[45]|hevc|avc|xvid|divx|av1|aac[0-9]*|e?ac3|dts(?:hd)?(?:ma)?|truehd|atmos|ddp[0-9]*|dd[0-9]+|flac|" +
        "amzn|hmax|dsnp|atvp|hulu|itunes|" +
        "mkv|mp4|avi|[0-9]+(?:[.,][0-9]+)?[mg]b|[0-9]{3,4}x[0-9]{3,4})$").toRegex()

internal val movieLanguageOrEditionTagPattern = ("^(?:german|deutsch|ger|dl|eng|ita|fre|fra|vf|vff|vostfr|truefrench|spa|esp|castellano|latino|rus|ukr|kor|jap|jpn|hindi|chs|cht|" +
        "multi|multisubs?|dual|dubbed|subbed|ensubbed|esubs?|omu|nlsubs?|hardsubs?|subs?|" +
        "unrated|uncut|remastered|extended|internal|proper|repack|rerip|limited|criterion|imax|rm4k|theatrical|retail|dircut)$").toRegex()

internal val trailingMovieTagPattern = ("(?i)\\s+(?:german|deutsch|ger|dl|eng|ita|multisubs?|dubbed|subbed|" +
        "unrated|uncut|remastered|extended|internal|proper|repack|limited|criterion|imax|rm4k|theatrical|retail|dircut|" +
        "directors?'?s?[\\s-]+cut|(?:extended|theatrical|unrated)[\\s-]+(?:cut|edition|version)|uncut[\\s-]+version|" +
        "(?:special|collectors?'?s?)[\\s-]+edition|(?:with|plus)[\\s-]+extras)$").toRegex()

// torrent hash and "title=" of magnet-like names, or the "Eto." (Russian "this is") of kinozal releases
internal val movieHashPrefixPattern = "(?i)^(?:[0-9a-f]{32,40}\\s*(?:title=)?|eto[\\s.]+(?=\\S))".toRegex()

// "Bubes.3.Taralad.20041080p" glues the year to the resolution
internal val yearGluedToResolutionPattern = "(?i)(?<=[^0-9])((?:19[0-9]{2}|20[0-2][0-9]))(?=(?:2160|1080|720|576|480)[pi])".toRegex()

internal val movieSitePrefixPattern = "(?i)^www\\.\\S+?\\.(?:com|org|net|me|to|tv|re|io|cc|ws)\\s*-?\\s+".toRegex()

internal val leadingBracketGroupsPattern = "^(?:\\s*\\[[^\\]]*])+".toRegex()

// list numbering of collections like "01. The Big Boss" or "a. Bruce", not titles like "12.Angry.Men"
internal val listNumberPrefixPattern = "^(?:[0-9]{1,3}[a-z]?|[a-z])\\.\\s+".toRegex(RegexOption.IGNORE_CASE)

internal val tokenPattern = "\\S+".toRegex()

internal val yearRangePattern = "(19[0-9]{2}|20[0-2][0-9])\\s*-\\s*(19[0-9]{2}|20[0-2][0-9]|[0-9]{2})(?![0-9])".toRegex()

internal val leadingYearTitlePattern = "^(19[0-9]{2}|20[0-2][0-9])\\s+(.*[a-zA-Z].*)$".toRegex()

internal val episodeLikeShortPattern = "^(?:e|s|se|pt|x)\\s?[0-9]+$".toRegex()

// series detection in the movies folder
internal val seasonEpisodeMarkerPattern = "(?i)(?<![a-z0-9])S[0-9]{1,2}[\\s._-]?E[0-9]{1,3}(?![0-9])".toRegex()

// 5x10, but not audio tags like 2.0x2 or resolutions like 1920x1080p
internal val seasonXEpisodeMarkerPattern = "(?<![0-9a-zA-Z.])(?:[1-9]|[0-9]{2})x[0-9]{2}(?![0-9a-zA-Z])".toRegex()

internal val trailingEpisodeNumberMarkerPattern = "\\s([0-9]{2,3})$".toRegex()

internal val seasonFolderMarkerPattern = "(?i)(?<![a-z0-9])(?:S[0-9]{1,2}|Season[\\s._-]*[0-9]{1,2})(?![0-9])".toRegex()

// files that aren't renamed at all
internal val creditlessFolderPattern = "(?i)^(?:nc|ncop|nced|ncop\\s*&\\s*nced|creditless(?:[\\s_.-].*)?)$".toRegex()

internal val discStructureRootNames = setOf("bdmv", "video_ts", "audio_ts", "certificate", "aacs")

internal val discStructureFolderNames = discStructureRootNames + setOf("stream", "playlist", "clipinf", "backup", "bdjo", "meta", "jar")

internal val coursePattern = "(?i)udemy|cbt[\\s._-]?nuggets?|freecoursesonline|masterclass|devcourseweb|lynda|pluralsight|coursera|skillshare|linkedin[\\s._-]learning".toRegex()

// ---- word lists of isJunk

/** Articles and connectors which are no title on their own. */
internal val junkArticles = setOf("the", "a", "an", "and", "or", "of", "in", "with", "&", "der", "die", "das", "le", "la", "les", "el", "los", "las", "il", "lo", "gli", "un", "une")

internal val countryAndLanguageTags = setOf("uk", "us", "usa", "ger", "fra", "fr", "de", "es", "ita", "it", "jp", "jpn", "kr", "kor", "cn", "chi", "ru", "rus", "eng", "en")

/** Folder and file names of extras, subtitles, samples, ... */
internal val auxiliaryExactKeywords = setOf(
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

/** Resolutions, codecs, sources and release sites, as they stand alone in a name. */
internal val technicalTags = setOf(
    "1080p", "720p", "2160p", "480p", "576p", "4k", "uhd", "fhd", "hdtv",
    "bluray", "blu-ray", "bdrip", "brrip", "dvdrip", "webrip", "web-dl", "webdl", "web", "remux", "proper", "repack", "rerip",
    "av1", "x264", "x265", "h264", "h265", "hevc", "avc", "xvid", "divx", "aac", "ac3", "dts", "ddp5 1", "ddp5.1", "10bit", "8bit", "hdr",
    "rarbg", "ettv", "eztv", "rartv", "tgx", "yts", "yify", "megusta"
)

internal val junkPrefixes = listOf(
    "season ", "seasons ", "disc ", "disk ", "cd ", "dvd ",
    "volume ", "vol ", "book ", "chapter ", "part ", "ep ", "episode ", "subs ", "subtitles ",
    "extras ", "featurette ", "featurettes ", "trailer ", "trailers ", "sample ", "samples ",
    "screenshot ", "screenshots ", "deleted scene", "deleted scenes", "making of ", "behind the scenes ",
    "story ", "temporada "
)

/** Every word which can be part of a name which is only junk. */
internal val junkTokens = categoryFolderNames + auxiliaryExactKeywords + technicalTags + setOf(
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

// ---- words of extractName

internal val leadingArticles = setOf("the", "a", "an", "der", "die", "das", "le", "la", "les", "el", "los", "las", "il", "lo", "gli", "un", "une")

internal val trailingPunctuation = setOf('-', '.', ',', ';', ':', '_', '~', '+', '!', '?', '\'', '"', '`', '/', '\\')

/** Library folders which hold shows, a file below one belongs to a show (or a pack of them). */
internal val seriesCategoryFolders = setOf("series-shows", "series shows", "series", "shows", "tv", "tv shows", "tv-shows", "tv series", "tv-series", "cartoons", "cartoon", "anime")

/** Words in the name of a folder which say it holds a release or a pack, not a single show. */
internal val releaseFolderMarkers = listOf("1080p", "720p", "x264", "dvdrip", "bluray", "complete", "season")

/** Acronyms of shows which are known by them. */
internal val showAcronyms = setOf("iasip", "btas", "tas", "cfwm", "sg1", "stargatesg1")

/** Acronyms which are accepted as a file name when they are not upper case. */
internal val acronymFileNames = setOf("iasip", "btas", "tas", "cfwm", "stargatesg1")

internal val showNamesOfAbbreviations = mapOf("iasip" to "Ifo Agokuc Sanif In Pufigikiganu", "stargatesg1" to "Safepicu SG-1")

/** Words which don't count when a folder name and a file name are compared. */
internal val overlapStopWords = setOf("the", "and", "for", "with", "from")

// ---- auxiliary files and folders (extras, samples, ...), see MediaNameParser.isAuxiliaryPathOrFile

/** Extensions of files which are not media and not worth a name. */
internal val auxiliaryNonMediaExtensions = setOf(
    "pdf", "txt", "nfo", "jpg", "jpeg", "png", "gif", "bmp", "doc", "docx",
    "xls", "xlsx", "exe", "zip", "rar", "7z", "tar", "gz", "sfv", "par2", "cue", "md5", "torrent", "url"
)

/** Names of folders which hold extras. */
internal val auxiliaryFolderKeywords = setOf(
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

/** Words in the name of a file which say it is an extra. */
internal val auxiliaryFileKeywords = listOf(
    "alternate ending", "alternate endings", "alternate opening", "alternate openings", "alternate cut",
    "alternate scene", "alternate scenes", "extended scene", "extended scenes", "extended cut",
    "featurette", "featurettes", "trailer", "trailers", "teaser", "teasers", "promo", "promos",
    "deleted scene", "deleted scenes", "screentest", "screentests", "outtake", "outtakes",
    "blooper", "bloopers", "gag reel", "gag reels", "screenshot", "screenshots", "sample", "samples",
    "character profile", "character profiles", "behind the scenes", "making of", "interview", "interviews",
    "original script", "original scripts", "textless", "special feature", "special features", "bonus", "bonuses",
    "gallery", "galleries", "photo gallery", "image gallery", "still gallery", "stills gallery", "slideshow", "slideshows"
)
