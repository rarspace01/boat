package boat

import boat.info.MediaItem
import boat.info.MediaProxyService
import boat.info.MediaType
import boat.utilities.StringUtil
import org.springframework.stereotype.Service
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.io.path.moveTo
import kotlin.math.abs

@Service
class ContentRenamer(val mediaProxyService: MediaProxyService) {

    companion object {
        val seasonPattern = "(?<=^|[^a-zA-Z])(?:S([0-9]{1,2})(?:[x._\\-\\s]?(?:E|EP)[0-9]{1,3})?|(?<![0-9])([0-9]{1,2})[x._\\-\\s]?(?:E|EP)[0-9]{1,3}(?=[^a-zA-Z0-9]|$))".toRegex(RegexOption.IGNORE_CASE)
        val seasonPattern2 = "(?:\\[|\\b)([0-9]{1,2})x([0-9]{1,2})(?:\\]|\\b)".toRegex()
        val episodePattern =
            "(?:(?<=^|[^a-zA-Z0-9])(?:Episode|Ep\\.?|E)|(?:\\s-\\s|\\s|-|\\.))([0-9]{1,3})(?=[^a-zA-Z0-9]|$)".toRegex(RegexOption.IGNORE_CASE)
        val standaloneEpisodePattern = "(?<=^|[\\s._-])(?<![0-9])([0-9]{3})(?![0-9])(?=[\\s._-]|$)".toRegex()
        val yearPattern = "\\b(19[0-9]{2}|20[0-2][0-9])\\b".toRegex()
        val bracketPattern = "\\[.*?]".toRegex()
        val bracketedEpisodePattern = "(?i)\\[(?:S?([0-9]{1,2})[x.]([0-9]{1,3})|S([0-9]{1,2})E([0-9]{1,3}))]".toRegex()
        // placeholders of BitComet like "_____padding_file_0_if you see this file, please update to BitComet 0.85 or above____"
        val paddingFilePattern = "(?i)^_+padding_file_[0-9]+_".toRegex()
        val dvdPattern = "(?<=^|\\s)(?:dvd|disc|cd)\\s?([0-9]{1,2})".toRegex(RegexOption.IGNORE_CASE)
        val dvdDirectPattern = "(?<=^|\\s)(?:dvd|disc|cd|book|volume|vol)\\s?([0-9]{1,2})".toRegex(RegexOption.IGNORE_CASE)
        val bookPattern = "(?<=^|\\s)(?:book|volume|vol|chapter)\\s?([0-9]{1,2})".toRegex(RegexOption.IGNORE_CASE)
        val seasonInNamePattern = "(?<=^|[\\s_.-])(?:Season|Seasons|Series(?=[\\s_.-]*[0-9]{1,2}(?:[^0-9]|$)))[\\s_.-]*(?:S?[0-9]{1,4}|[0-9]{1,2}[-–_~to]+(?:S?[0-9]{1,2})?|S[0-9]{2}[-–_~to]?S?[0-9]{2})?".toRegex(RegexOption.IGNORE_CASE)
        val letterOSeasonPattern = "(?<=^|[^a-zA-Z0-9])[Ss][Oo](?=[0-9][Ee][0-9])".toRegex()
        val seasonDirectPatternInName = "(?<=(?:Season|Day)[\\s_.-]?)([0-9]{1,2})".toRegex(RegexOption.IGNORE_CASE)
        val dayInNamePattern = "(?<=^|[\\s_.-])Day\\s([0-9]{1,2})".toRegex(RegexOption.IGNORE_CASE)
        val packInNamePattern = "(?<=^|[\\s_.-])(?:Complete(?:[\\s_.-]+(?:Series|Season|Seasons|Collection|Set|Pack|Boxset|Box[\\s_.-]+Set|With[\\s_.-]+Subtitles))?|Completo|All[\\s_.-]+(?:Series|Season|Seasons|Episodes|Eps|Collection|Set|Pack|Boxset|Box[\\s_.-]+Set)|Entire[\\s_.-]+(?:Series|Season|Seasons|Collection|Set|Pack|Boxset|Box[\\s_.-]+Set)|Full[\\s_.-]+(?:Series|Season|Seasons|Collection|Set|Pack|Boxset|Box[\\s_.-]+Set)|(?:Boxset|Box[\\s_.-]+Set|Collection|Collector(?:'s|s)?[\\s_.-]+Edition)|With[\\s_.-]+(?:Subtitles|Subs)|Dual[\\s_.-]+Audio|Multi[- ]Audio|Multi[- ]Subs|Tv[\\s_.-]+Series(?:[\\s_.-]+(?:Season|Seasons))?)(?=\\b|[\\s_.-]|$)".toRegex(RegexOption.IGNORE_CASE)
        val extrasInNamePattern = "(?<=^|[\\s_.-])(?:Extras|Extra|Featurettes|Featurette|Bonus|Subs|Subtitles|Gallery|Galleries)(?=\\b|[\\s_.-]|$)".toRegex(RegexOption.IGNORE_CASE)
        val startsWithEpisodeNumberPattern = "^(?:[0-9]{1,3}\\s?[.x-]\\s?[0-9]{1,3}\\s?-\\s?|(?:19[0-9]{2}|20[0-2][0-9])[eExX][0-9]{1,3}[-._\\s]?|S?[0-9]{1,2}[x._\\-\\s]?(?:E|EP)[0-9]{1,3}[-._\\s]?|[0-9]{1,2}x[0-9]{1,2}[-._\\s]?)".toRegex(RegexOption.IGNORE_CASE)

        // "www.Torrenting.org - 90 Day Fiance", "www.Torrenting.org       90 Day Fiance" or "Www.Torrenting.Com.90.Day.Fiance"
        val sitePrefixPattern = "^www[._ ]+[a-z0-9-]+[._ ]+(?:com|org|net|me|to|tv|re|io)(?:\\s*-\\s*|[\\s._]+)".toRegex(RegexOption.IGNORE_CASE)
        val parenthesizedPrefixPattern = "^\\([a-zA-Z0-9.-]+\\)".toRegex()
        val groupPrefixPattern = "^([0-9a-f]{4,10}-)".toRegex(RegexOption.IGNORE_CASE)
        val unitPattern = "^[0-9]+-(?:lb|lb\\.|pound|foot|inch|mile|meter|gram|ton|year|day|hour|min|sec|part|way|ways).*".toRegex()
        val parenthesizedTagsPattern = "(?i)\\((?:[^)]*\\b(?:[0-9]{3,4}p|bluray|blu-ray|dvd|bdrip|dvdrip|web-dl|webdl|webrip|h264|x264|h265|x265|hevc|xvid|divx|dual audio|multi audio|multi subs|proper|repack|sub|subs|dub|dubbed|ger[._ -]?sub|eng[._ -]?sub|moviesbyrizzo|bbc[._ -]*[0-9]{4}|[0-9]{4}\\s*-\\s*[0-9]{2,4}|open matte version|theatrical version|subbed|french|korean|german|japanese|spanish|[0-9a-f]{8})\\b[^)]*)\\)".toRegex()
        val parenthesizedGroupPattern = "\\([^)]*\\)".toRegex()
        val parenthesizedEpisodeRangePattern = "\\([0-9]{1,3}-[0-9]{1,3}\\)".toRegex()
        val episodeRangePattern = "(?<=[a-zA-Z\\s_])[0-9]{1,3}-[0-9]{1,3}(?=[\\s_]*(?:\\+|$|\\b))".toRegex()
        val parenthesizedYearPattern = "[(\\[](?:19[0-9]{2}|20[0-2][0-9])(?:-[0-9]{2,4})?[)\\]]".toRegex()
        // copy counter or cut off word after the year like "Anger.Management.(2011).2" or "Bbc.Planet.Earth.(2006).Se"
        val copyCounterAfterParenthesizedYearPattern = "(?<=[(\\[](?:19|20)[0-9]{2}[)\\]])[._\\s]+(?:[0-9]|[a-zA-Z]{1,2})$".toRegex()
        val yearEpisodePrefixPattern = "^(?:19[0-9]{2}|20[0-2][0-9])[eExX][0-9]{1,3}[-._\\s]?".toRegex(RegexOption.IGNORE_CASE)
        val versionPattern = "(?<=[0-9])v[0-9]+".toRegex()
        val numericRangePattern = "^[0-9]+(?:[-.][0-9]+)+$".toRegex()
        val episodeWithTitlePattern = "(?:\\s-\\s|\\s-_|_-_|\\s-\\s?|_-\\s?|\\s|^)(?:Episode|Ep\\.?|E)?\\s*([0-9]{1,3})\\s+-\\s+[a-zA-Z]".toRegex(RegexOption.IGNORE_CASE)
        val dashEpisodePattern = "(?:\\s-\\s|\\s-_|_-_|\\s-\\s?|_-\\s?)([0-9]{1,3})(?=[^a-zA-Z0-9]|$)".toRegex()
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
        // the folder of a show Plex lists as featurettes
        const val featurettesFolder = "Featurettes"
        // S01E101 is episode 101, absolute numbering in a single season like Dragon Ball's
        val explicitSeasonEpisodePattern = "(?i)(?<![a-z0-9])S([0-9]{1,2})[\\s._-]?E([0-9]{1,4})(?![0-9])".toRegex()
        // specials attached to an episode like "Lost S01E20X01 Lost The Journey"
        val episodeSpecialPattern = "(?i)(?<![a-z0-9])S[0-9]{1,2}[\\s._-]?E[0-9]{1,4}X[0-9]{1,3}(?![0-9])".toRegex()
        // double episodes like "Lost.S04E13E14 'There's No Place Like Home - Part 2'", not "S04E05-1960s.Week"
        val doubleEpisodePattern = "(?i)(?<![a-z0-9])S[0-9]{1,2}[\\s._-]?E([0-9]{1,4})(?:-?E|-)([0-9]{1,4})(?![0-9a-z])".toRegex()
        // segments of an episode like "Kim Possible - S02 E06b - Adventures In Rufus-Sitting"
        val episodePartPattern = "(?i)(?<![a-z0-9])S[0-9]{1,2}[\\s._-]?E[0-9]{1,4}([a-z])(?![a-z0-9])".toRegex()
        // daily shows like "Dr.Phil.Show.2013.10.01.Dangerous.and.Violent.Love" have no season and episode in the name
        val airDatePattern = "(?<![0-9])(?:19|20)[0-9]{2}[._\\s-](?:0[1-9]|1[0-2])[._\\s-](?:0[1-9]|[12][0-9]|3[01])(?![0-9])".toRegex()
        // parts of a mini series like "The Hitchhikers Guide To The Galaxy - Part 1 Of 6"
        val partOfPattern = "(?i)(?<![a-z])part\\s*([0-9]{1,2})\\s*of\\s*[0-9]{1,2}(?![0-9])".toRegex()
        // "No Game No Life 12 Rule Number 10" or "Eden of the East - 01 - I've Found Myself a Prince", three digits like
        // "Duckman 101 - I, Duckman" can be season and episode
        val episodeAfterShowPattern = "^(?:\\[[^\\]]*][\\s._-]*)?[A-Za-z][^0-9]*?[\\s._-]+(?:-[\\s._-]+)?([0-9]{2})(?:[\\s._]+-)?[\\s._]+[A-Za-z]".toRegex()
        // numbers like "Tokyo Magnitude 8.0" or audio channels like "AAC 5.1" aren't episodes
        val decimalNumberPattern = "(?<![0-9.])[0-9]\\.[0-9](?![0-9.])".toRegex()
        // audio channels after the codec, also with spaces like "EAC3 2 0" or "DDP 5 1"
        val audioChannelsPattern = "(?i)(?<=aac|ac3|eac3|ddp|dd|dts|opus|flac|mp3)[\\s._-]*[0-9][\\s._][0-9](?![0-9])".toRegex()
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
        val nonAlphanumericPattern = Regex("[^a-z0-9]+")
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

        val yearEpisodePattern = "(?<=^|[^a-zA-Z0-9])(?:19[0-9]{2}|20[0-2][0-9])[eE]([0-9]{1,3})(?=[^a-zA-Z0-9]|$)".toRegex(RegexOption.IGNORE_CASE)
        // season and episode glued to the title like "Shameless901.Are You There Shim"
        val gluedSeasonEpisodePattern = "^([^\\s\\[(]*?[a-zA-Z]{3})([1-9][0-9]?)([0-9]{2})(?=[\\s._-]|$)".toRegex()
        val yearSpecialPattern = "(?i)(?<=^|[^a-zA-Z0-9])(?:19[0-9]{2}|20[0-2][0-9])(?:e[0-9]{1,3})?x[0-9]{1,3}(?=[^a-zA-Z0-9]|$)".toRegex()
        // openings, endings and OVAs go to the extras of the show, OP and ED only in upper case, they're words otherwise ("Ed, Edd n Eddy")
        // the folder of a show Plex lists as "Other" extras
        const val otherExtrasFolder = "Other"
        val ovaMarkerPattern = "(?i)(?<![a-z])(?:NCOP|NCED|OVA|OAD)s?(?![a-z])".toRegex()
        val openingEndingMarkerPattern = "(?<![A-Za-z])(?:OP|ED)(?:[ ._]?[0-9]{1,2}(?:v[0-9])?)?(?![A-Za-z0-9])".toRegex()
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
        val titleStopWords = setOf("the", "and", "of", "a", "an", "in", "on", "to", "der", "die", "das")
        // "Captain.Future.Folge.13-19" or "Eden.of.the.East.Episode.10" before the dots become spaces
        val episodeWordPattern = "(?i)(?<=[\\s._-])(?:Folge|Episode)(?=[\\s._-]*[0-9])".toRegex()
        // "hak5--1718--free-virtualization-clusters" is season 17 episode 18
        val doubleDashEpisodePattern = "--([0-9]{1,2})([0-9]{2})--".toRegex()
        // files with several episodes like "Captain.Future.Folge.01-06" have no single episode
        val episodeRangeWordPattern = "(?i)(?<![a-z])(?:Folge|Episodes?|Eps?)[\\s._-]*[0-9]{1,3}-[0-9]{1,3}(?![0-9])".toRegex()
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
        val yearEpisodeDirectPattern = "(?<=^|[^a-zA-Z0-9])(19[0-9]{2}|20[0-2][0-9])[eExX][0-9]{1,3}(?:[xX][0-9]{1,3})?(?=[^a-zA-Z0-9]|$)".toRegex(RegexOption.IGNORE_CASE)
        val explicitEpisodeLookbehindPattern = "(?<=^|[^a-zA-Z0-9])(?:Episode|Ep\\.?|E)\\s*([0-9]{1,3})(?=[^a-zA-Z0-9]|$)".toRegex(RegexOption.IGNORE_CASE)
        val zeroPaddedSeasonEpisodePattern = "(?<=^|[^a-zA-Z0-9])(0[1-9][0-9]{2})(?=[^a-zA-Z0-9]|$)".toRegex()
        val fourDigitYearPattern = "\\b([1-2][0-9]{3})\\b".toRegex()
        val seasonEpisodePattern = "(?<=^|[^a-zA-Z])(?:S[0-9]{1,2}[x._\\-\\s]?(?:E|EP)|(?<![0-9])[0-9]{1,2}[eE])([0-9]{1,3})(?=[^a-zA-Z0-9]|$)".toRegex(RegexOption.IGNORE_CASE)
        val acronymEpisodePattern = "(?<=^|[^a-zA-Z0-9])([a-zA-Z]{1,4})([0-9]{1,3})(?=[^a-zA-Z0-9]|$)".toRegex(RegexOption.IGNORE_CASE)
        val dashTrailingNumberPattern = "-([0-9]{1,3})\\.".toRegex()
        val zeroPadded3DigitsPattern = Regex("(?<=^|[^0-9])0[0-9]{2}(?=[^0-9]|$)")

        // any height like 360p, 405p, 1080i or 1440p, also with a frame rate like "hd720p30"
        val resolutionPattern = "(?i)(?<![a-z0-9])(?:hd)?([0-9]{3,4})([pi])(?:[0-9]{2})?(?![a-z0-9])".toRegex()
        val kResolutionPattern = "(?i)(?<![a-z0-9])([248]k)(?![a-z0-9])".toRegex()
        // width and height like 1920x1080 or 1280x960
        val dimensionsPattern = "(?<![0-9])([0-9]{3,4})x([0-9]{3,4})(?![0-9])".toRegex()
        private val resolutionHeights = 240..4320
        val codecPattern = "(?i)(?<=^|[^a-zA-Z0-9])(x265|x264|h265|h264|hevc|avc|xvid|divx|av1|vp9|h\\.264|h\\.265|x\\.264|x\\.265)(?=[^a-zA-Z0-9]|$)".toRegex()
        val movieExtraPattern = "(?i)(?<=^|[^a-zA-Z0-9])(?:sample|trailer|trailers|featurette|featurettes|extra|extras|bonus|behind[\\s_.-]?the[\\s_.-]?scenes|deleted[\\s_.-]scenes?|interviews?)(?=[^a-zA-Z0-9]|$)".toRegex()
        val moviePartPattern = "(?i)(?<=^|[\\s._\\-\\[(])(?:cd|disc|disk|part|pt)[\\s._\\-]?([0-9]{1,2})(?=$|[\\s._\\-\\])])".toRegex()
        val subtitleExtensions = setOf("srt", "sub", "idx", "ass", "ssa", "vtt", "sup")
        val videoExtensions = setOf("mkv", "mp4", "avi", "m4v", "mov", "wmv", "mpg", "mpeg", "ts", "m2ts", "webm", "flv", "divx", "ogm")
        val remoteMediaIdPattern = "\\{(?:tmdb|tvdb)-[0-9]+}".toRegex(RegexOption.IGNORE_CASE)
        val releaseGroups: Set<String> = ContentRenamer::class.java.getResourceAsStream("/releasegroups.txt")
            ?.bufferedReader()
            ?.useLines { lines -> lines.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.map { it.lowercase() }.toSet() }
            ?: emptySet()
        private val releaseGroupAlternatives = releaseGroups.joinToString("|") { Regex.escape(it) }
        private val leadingReleaseGroupPattern = "^(?:$releaseGroupAlternatives)-(?=.)".toRegex(RegexOption.IGNORE_CASE)
        private val trailingReleaseGroupPattern = "(?<=.)-(?:$releaseGroupAlternatives)$".toRegex(RegexOption.IGNORE_CASE)
        // ISO 639-1 and 639-2/T codes, plus the 639-2/B variants commonly used for subtitles (e.g. ger instead of deu)
        // English language names to their code, e.g. "danish" to "da"
        private val languageNames = java.util.Locale.getISOLanguages().associateBy { java.util.Locale.of(it).getDisplayLanguage(java.util.Locale.ENGLISH).lowercase() }
        val nonLetterPattern = "[^a-z]+".toRegex()
        private val languageCodes = java.util.Locale.getISOLanguages().flatMap { listOfNotNull(it, runCatching { java.util.Locale.of(it).isO3Language }.getOrNull()) }.toSet() +
                setOf("alb", "arm", "baq", "bur", "chi", "cze", "dut", "fre", "geo", "ger", "gre", "ice", "mac", "mao", "may", "per", "rum", "slo", "tib", "wel")
        val invalidFileCharsPattern = Regex("[\\\\/:*?\"<>|]")

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
                "(?:b[dr]|hd|dvd|vhs|web|fhd|tv|hdtv|sat|dvb)rip|bluray|blu-ray|bdremux|remux|dvd[59r]?|dvdscr|webdl|web-dl|hdtv|hdcam|screener|ntsc|" +
                "x26[45]|h26[45]|hevc|avc|xvid|divx|av1|aac[0-9]*|e?ac3|dts(?:hd)?(?:ma)?|truehd|atmos|ddp[0-9]*|dd[0-9]+|flac|" +
                "amzn|hmax|dsnp|atvp|hulu|itunes|" +
                "mkv|mp4|avi|[0-9]+(?:[.,][0-9]+)?[mg]b|[0-9]{3,4}x[0-9]{3,4})$").toRegex()
        val movieLanguageOrEditionTagPattern = ("^(?:german|deutsch|ger|dl|eng|ita|fre|fra|vf|vff|vostfr|truefrench|spa|esp|castellano|latino|rus|ukr|kor|jap|jpn|hindi|chs|cht|" +
                "multi|multisubs?|dual|dubbed|subbed|ensubbed|esubs?|omu|nlsubs?|hardsubs?|subs?|" +
                "unrated|uncut|remastered|extended|internal|proper|repack|rerip|limited|criterion|imax|rm4k|theatrical|retail)$").toRegex()
        val trailingMovieTagPattern = ("(?i)\\s+(?:german|deutsch|ger|dl|eng|ita|multisubs?|dubbed|subbed|" +
                "unrated|uncut|remastered|extended|internal|proper|repack|limited|criterion|imax|rm4k|theatrical|retail|" +
                "directors?'?s?[\\s-]+cut|(?:extended|theatrical|unrated)[\\s-]+(?:cut|edition|version)|uncut[\\s-]+version|" +
                "(?:special|collectors?'?s?)[\\s-]+edition|(?:with|plus)[\\s-]+extras)$").toRegex()
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

    /**
     * Removes known release groups attached with "-", e.g. "sparks-inception" or "Inception.2010.x264-SPARKS".
     */
    fun removeReleaseGroups(name: String): String {
        if (releaseGroups.isEmpty()) return name
        return name.replace(leadingReleaseGroupPattern, "").replace(trailingReleaseGroupPattern, "")
    }

    /**
     * Files worth renaming for a movie, everything else (nfo, txt, images, ...) is left untouched.
     */
    fun isMovieFile(file: File): Boolean {
        val extension = file.extension.lowercase()
        return extension in videoExtensions || extension in subtitleExtensions
    }

    fun cleanName(inputName: String): String {
        var name = inputName
        // Normalize unicode and foreign characters, remove backslash escape characters
        name = name.replace("：", " ").replace("?", " ").replace("\\", "")

        // the title ends at a bracketed episode like "House - [2x01] - Acceptance" or "Dragon Ball GT [1.21] A Secret Revealed"
        val bracketedEpisode = bracketedEpisodePattern.find(name)
        if (bracketedEpisode != null && bracketedEpisode.range.first > 0) {
            name = name.substring(0, bracketedEpisode.range.first)
        }

        // Remove bracketed release info like [UNCENSORED], [1080p], [5.1 MP3], [x265], [FINAL]
        name = name.replace(bracketPattern, " ").trim()

        // Remove common release group/site prefixes
        name = name.replace(sitePrefixPattern, "")
        name = removeReleaseGroups(name)

        // Remove remote media ids of already renamed files like {tmdb-27205}
        name = name.replace(remoteMediaIdPattern, " ").trim()

        // Remove release tag prefix like (B-A) or [GAX]
        name = name.replace(parenthesizedPrefixPattern, "")

        // check if it's a known hex garbage prefix (containing digits)
        val match = groupPrefixPattern.find(name)
        if (match != null && match.value.any { it.isDigit() } && !name.lowercase().startsWith("star") && !name.lowercase().startsWith("hack") &&
            !name.lowercase().matches(unitPattern)
        ) {
            name = name.replace(groupPrefixPattern, "")
        }

        // Remove parenthesized tags like (BBC 1995), (2013 BluRay - 1080p DUAL AUDIO), (ger.sub), (h264), (moviesbyrizzo), (720p - DUAL Audio), (1BB88E0D)
        // underscores are word characters and hide tags like (1520x1080_Blu-Ray_FLAC) from the word boundaries
        name = name.replace(parenthesizedGroupPattern) { it.value.replace("_", " ") }
        name = name.replace(parenthesizedTagsPattern, " ")

        name = name.replace(parenthesizedEpisodeRangePattern, " ")

        name = name.replace(episodeRangePattern, " ")

        val hasParenthesizedYear = parenthesizedYearPattern.containsMatchIn(name)
        if (hasParenthesizedYear) {
            name = name.replace(copyCounterAfterParenthesizedYearPattern, "")
        }
        name = name.replace(parenthesizedYearPattern, " ")
        name = name.replace("(", "").replace(")", "").replace("_", " ").trim()
        name = name.removePrefix(".")

        name = name.replace(yearEpisodePrefixPattern, "")

        // Remove version markers like v2 in 01v2
        name = name.replace(versionPattern, "")

        // release tags end the title, the season and episode detection works on what's left
        val technicalTagMatch = midNameTechnicalTagPattern.find(name)
        if (technicalTagMatch != null && technicalTagMatch.range.first > 0) {
            name = name.substring(0, technicalTagMatch.range.first).trim()
        }

        var truncated = false
        val seasonMatch = seasonPattern.find(name)
        val seasonMatch2 = seasonPattern2.find(name)
        val seasonMatch3 = seasonInNamePattern.find(name)
        val yearEpisodeMatch = yearEpisodeInNamePattern.find(name)
        val partMatch = partInNamePattern.find(name)
        val episodeWordMatch = episodeWordPattern.find(name)
        val doubleDashEpisodeMatch = doubleDashEpisodePattern.find(name)
        val specialMarkerIndex = specialMarkerIndex(name, casingReference = inputName)
        val dayMatch = dayInNamePattern.find(name)
        val dvdMatch = dvdPattern.find(name)
        val bookMatch = bookPattern.find(name)
        val packMatch = packInNamePattern.find(name)
        val extrasMatch = extrasInNamePattern.find(name)

        val potentialSeasonMatches = listOfNotNull(
            seasonMatch?.range?.first,
            seasonMatch2?.range?.first,
            seasonMatch3?.range?.first,
            if (yearEpisodeMatch != null && yearEpisodeMatch.range.first > 0) yearEpisodeMatch.range.first else null,
            if (partMatch != null && partMatch.range.first > 0) partMatch.range.first else null,
            if (episodeWordMatch != null && episodeWordMatch.range.first > 0) episodeWordMatch.range.first else null,
            if (doubleDashEpisodeMatch != null && doubleDashEpisodeMatch.range.first > 0) doubleDashEpisodeMatch.range.first else null,
            specialMarkerIndex?.takeIf { it > 0 },
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
        } else if (!name.matches(numericRangePattern)) {
            val epWithTitleMatch = episodeWithTitlePattern.find(name)
            val dashEpMatch = dashEpisodePattern.find(name)
            val explicitEpMatch = explicitEpisodePattern.find(name)
            if (epWithTitleMatch != null) {
                name = name.substring(0, epWithTitleMatch.range.first)
                truncated = true
            } else if (explicitEpMatch != null) {
                name = name.substring(0, explicitEpMatch.range.first)
                truncated = true
            } else if (dashEpMatch != null) {
                name = name.substring(0, dashEpMatch.range.first)
                truncated = true
            } else {
                val episodeMatch = cleanEpisodeMatchPattern.find(name)
                if (episodeMatch != null) {
                    name = name.substring(0, episodeMatch.range.first)
                    truncated = true
                } else {
                    val standaloneEpisodeMatch = standaloneEpisodePattern.find(name)
                    val zeroPaddedMatch = zeroPaddedMatchPattern.find(name)
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
        name = name.replace(acronymPattern) { matchResult ->
            matchResult.value.replace(".", "")
        }
        name = name.replace(".", " ").replace("_", " ").trim()
        name = name.replace(extrasPlusPattern, " ")
        name = name.replace(episodeRangePattern, " ")
        name = name.replace(whitespacePattern, " ").trim()

        val trailingPunctuation = setOf('-', '.', ',', ';', ':', '_', '~', '+', '!', '?', '\'', '"', '`', '/', '\\')
        while (true) {
            val before = name
            name = name.replace(trailingQualityTag, "").trim()
            if (!hasParenthesizedYear) {
                name = name.replace(trailingYearPattern, "").trim()
                if (truncated) {
                    name = name.replace(trailingYearAfterNumberPattern, "").trim()
                }
            }
            name = name.replace(trailingEpisodeMarkerPattern, "").trim()
            name = name.replace(trailingDashArticlePattern, "").trim()
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
            val trailingMatch = trailingEpisodePattern.find(name)
            if (trailingMatch != null) {
                name = name.substring(0, trailingMatch.range.first).trim()
            } else {
                // If name has a number followed by space and another number (e.g. Golgo 13 20), truncate the trailing episode
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
        if (releaseGroups.contains(lower)) return true

        val articles = setOf("the", "a", "an", "and", "or", "of", "in", "with", "&", "der", "die", "das", "le", "la", "les", "el", "los", "las", "il", "lo", "gli", "un", "une")
        if (articles.contains(lower) || articles.contains(normalized)) return true

        if (categoryFolderNames.contains(lower) || categoryFolderNames.contains(normalized)) return true

        // Range folder names like "0-9", "a-z", etc.
        if (lower.matches(rangeSinglePattern) || lower.matches(rangeLetterPattern)) return true

        // Magnet URI or URLs
        if (lower.startsWith("magnet:") || lower.contains("xt=urn:") || lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("www.")) return true
        if (lower.endsWith(".com") || lower.endsWith(".org") || lower.endsWith(".net") || lower.endsWith(".me") || lower.endsWith(".to") || lower.endsWith(".tv") || lower.endsWith(".io")) return true

        // Pure numbers, or numbered sub folders like "2a. Season 1 (2010-11)"
        if (lower.matches(pureNumberPattern) || lower.matches(numberedSubFolderPattern)) return true

        // Pure hex hashes (e.g. 1E372EE5, BB605406)
        if (lower.matches(hexHashPattern) && lower.any { it.isDigit() } && lower.any { it.isLetter() }) return true

        // Season/Episode/Part/Volume/Disc patterns
        if (normalized.matches(seasonDiscPartPattern)) return true
        if (normalized.matches(sEPattern)) return true
        if (normalized.matches(xPattern)) return true
        if (normalized.matches(dashDotNumPattern)) return true
        if (normalized.matches(shortWordNumPattern) && !normalized.startsWith("the ") && !normalized.startsWith("a ") && !normalized.startsWith("an ")) return true
        if (normalized.matches(numShortWordPattern)) return true // e.g. "6 spa", "6_spa"

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
            "story ", "temporada "
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

        val tokenNormalized = trimmed.lowercase().replace(nonAlphanumericPattern, " ").trim()
        val tokens = tokenNormalized.split(" ").filter { it.isNotBlank() }
        val hasKnownJunkOrTechToken = tokens.any { allTechnicalAndJunkTokens.contains(it) }
        if (hasKnownJunkOrTechToken && tokens.all { token ->
                token.length <= 1 || token.matches(pureNumberPattern) || allTechnicalAndJunkTokens.contains(token)
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
            val pTokens = pNormalized.split(nonAlphanumericPattern).filter { it.isNotBlank() }

            val isPackFolder = pName.contains("+") || pNormalized.contains("with extras") || pNormalized.contains("and extras") ||
                    pNormalized.matches(packWithExtrasPattern)

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
        val cleanedFileName = cleanName(gluedSeasonEpisode(file)?.first ?: rawBaseName)
        val cleanedFileNameWithoutTrackNumber = cleanName(rawBaseName.replace(leadingTrackNumberPattern, ""))

        val isNumericShowName = cleanedFileName.matches(pureNumberPattern) &&
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

        val isShortAcronym = cleanedFileName.matches(shortAcronymPattern) &&
                (cleanedFileName == cleanedFileName.uppercase() || cleanedFileName.lowercase() in setOf("iasip", "btas", "tas", "cfwm", "stargatesg1"))


        // Search upwards in parent directory hierarchy for candidates
        val candidates = mutableListOf<String>()
        var currentFile: File? = file.parentFile
        var depth = 0
        var isInsidePackOrSeriesFolder = false
        var isBelowExtrasContainer = false
        while (currentFile != null && depth < 5) {
            val parentName = decodeIfEncoded(currentFile.name)
            val lowerParent = parentName.lowercase().trim()
            val normalizedParent = lowerParent.replace("_", " ").replace(".", " ").replace("-", " ").trim()

            // Stop traversing upwards if we reached a single letter folder, symbol folder, range folder, or root category / filesystem folder
            if (parentName.trim().length <= 1 ||
                parentName.trim().all { !it.isLetterOrDigit() } ||
                lowerParent.matches(rangeSinglePattern) ||
                lowerParent.matches(rangeLetterPattern) ||
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

            val isExtrasContainer = isExtrasContainer(parentName)
            if (isExtrasContainer) {
                isBelowExtrasContainer = true
                candidates.clear()
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
            val isParentNumericShowName = cleanedParentName.matches(pureNumberPattern) &&
                    !cleanedParentName.startsWith("0") &&
                    (parentName != cleanedParentName || currentFile.parentFile?.let { p ->
                        val pName = decodeIfEncoded(p.name).lowercase().trim()
                        pName.matches(rangeSinglePattern) || categoryFolderNames.contains(pName)
                    } == true)

            if (!isExtrasContainer && cleanedParentName.isNotBlank() && (!isJunk(cleanedParentName) && !isJunk(parentName) || isParentNumericShowName)) {
                candidates.add(cleanedParentName)
            }
            currentFile = currentFile.parentFile
            depth++
        }

        val combinedParentShowName: String? = if (candidates.isNotEmpty()) {
            val reversed = candidates.reversed()
            var combined = reversed.first()
            for (i in 1 until reversed.size) {
                val next = reversed[i]
                val lowerCombined = combined.lowercase()
                val lowerNext = next.lowercase()
                val normCombined = lowerCombined.replace(nonAlphanumericOnlyPattern, "")
                val normNext = lowerNext.replace(nonAlphanumericOnlyPattern, "")
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
        val parentShowName = combinedParentShowName?.let { removeTrailingSeasonNumber(it, rawBaseName) }

        if (!isCandidate0Junk && cleanedFileName.isNotBlank() && (!isShortAcronym || file.parentFile == null)) {
            if (!parentShowName.isNullOrBlank()) {
                // the file name of an extra describes the extra, e.g. "DVD Extras/Deep Space Nine Sketchbook.mkv"
                if (isBelowExtrasContainer) {
                    return showFromFileNameIfFolderAddsWords(cleanedFileNameWithoutTrackNumber, parentShowName) ?: parentShowName
                }
                // "Full Metal Panic 10 Run, Running, Ran" in "Full Metal Panic", episodes below 10 are already cut as zero padded number
                // or "01. Crest of the Stars" and "1-01 Gigantor (US) S01E01" with a leading track number
                val normalizedParentShowName = parentShowName.lowercase().replace(nonAlphanumericOnlyPattern, "")
                val episodeNumberMatch = episodeNumberWithTitlePattern.find(cleanedFileName)
                val showBeforeEpisodeNumber = episodeNumberMatch?.groupValues?.get(1)
                val normalizedShowBeforeEpisodeNumber = showBeforeEpisodeNumber?.lowercase()?.replace(nonAlphanumericOnlyPattern, "")
                if (normalizedShowBeforeEpisodeNumber != null && normalizedShowBeforeEpisodeNumber.startsWith(normalizedParentShowName)) {
                    return parentShowName
                }
                // the folder carries more than the show, e.g. "No_Game_No_Life_English_Dub_720p_1-12_Complete"
                // but the number isn't part of the show like "Space Battleship Yamato 2199"
                if (normalizedShowBeforeEpisodeNumber != null && showBeforeEpisodeNumber.trim().contains(' ') &&
                    normalizedParentShowName.startsWith(normalizedShowBeforeEpisodeNumber) &&
                    !normalizedParentShowName.startsWith(normalizedShowBeforeEpisodeNumber + episodeNumberMatch.groupValues[2])
                ) {
                    return showBeforeEpisodeNumber.trim()
                }
                val fileNameAfterTrackNumber = rawBaseName.replace(leadingTrackNumberPattern, "")
                if (fileNameAfterTrackNumber != rawBaseName) {
                    val normalizedFileName = cleanName(fileNameAfterTrackNumber).lowercase().replace(nonAlphanumericOnlyPattern, "")
                    if (normalizedFileName.isNotEmpty() &&
                        (normalizedFileName.startsWith(normalizedParentShowName) || normalizedParentShowName.startsWith(normalizedFileName))
                    ) {
                        return parentShowName
                    }
                }
                val remainder = if (cleanedFileName.startsWith(parentShowName, ignoreCase = true)) {
                    cleanedFileName.substring(parentShowName.length).trim()
                } else {
                    cleanedFileName.removePrefix(parentShowName).trim()
                }
                val isNumberPartOfTitle = remainder.startsWith("-") && seasonEpisodePattern.containsMatchIn(rawBaseName)
                if (remainder.isEmpty() || remainder.matches(remainderEpisodePattern) && !isNumberPartOfTitle) {
                    return parentShowName
                }
                val remainderParent = if (parentShowName.startsWith(cleanedFileName, ignoreCase = true)) {
                    parentShowName.substring(cleanedFileName.length).trim()
                } else {
                    parentShowName.removePrefix(cleanedFileName).trim()
                }
                if (remainderParent.isEmpty() || remainderParent.matches(remainderYearOrNumPattern)) {
                    return parentShowName
                }
                if (isInsidePackOrSeriesFolder) {
                    val parentWords = parentShowName.lowercase().split(nonAlphanumericPattern).filter { it.length > 2 && it !in setOf("the", "and", "for", "with", "from") }
                    val fileWords = cleanedFileName.lowercase().split(nonAlphanumericPattern).filter { it.length > 2 && it !in setOf("the", "and", "for", "with", "from") }
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
            if (!isJunk(cleanedFileNameWithoutTrackNumber)) {
                showFromFileNameIfFolderAddsWords(cleanedFileNameWithoutTrackNumber, parentShowName)?.let { return it }
            }
            return parentShowName
        }

        showAfterEpisodeMarkerPattern.find(rawBaseName)?.let { return cleanName(it.groupValues[1]) }
        doubleDashEpisodePattern.find(rawBaseName)?.takeIf { it.range.first > 0 }?.let { return cleanName(rawBaseName.substring(0, it.range.first)) }
        return ""
    }

    /**
     * The file name if the folder only adds words to it, e.g. "Pride and Prejudice" in "Pride and Prejudice Jane Austen".
     * A single added word like "The Office US" is kept, it tells versions of a show apart.
     */
    private fun showFromFileNameIfFolderAddsWords(fileShowName: String, folderShowName: String): String? {
        if (!fileShowName.contains(' ') || !folderShowName.startsWith(fileShowName, ignoreCase = true)) return null
        val addedWords = folderShowName.substring(fileShowName.length).trim().split(whitespacePattern).filter { it.isNotBlank() }
        return if (addedWords.size >= 2 && addedWords.none { word -> word.any { it.isDigit() } }) fileShowName else null
    }

    /**
     * Season folders like "Jessica.Jones.3.(2019)" or "Takeshis.Castle.1" carry the season as trailing number,
     * it's only removed if the file name confirms that season, e.g. "S03E01" or "Takeshis Castle - 101".
     */
    private fun removeTrailingSeasonNumber(showName: String, fileBaseName: String): String {
        val season = trailingSeasonNumberPattern.find(showName)?.groupValues?.get(1)?.toIntOrNull() ?: return showName
        val seasonInFileName = "(?i)(?:\\bs0*$season\\s?e[0-9]|\\b$season[x][0-9]|(?:^|[\\s_.-])$season[0-9]{2}(?:[\\s_.\\[]|$))".toRegex()
        return if (seasonInFileName.containsMatchIn(fileBaseName)) showName.replace(trailingSeasonNumberPattern, "").trim() else showName
    }

    /**
     * Movie title and year from the file name, or from the closest folder if the file name doesn't carry a title
     * (e.g. cd1.mkv, sample.mkv, RARBG.txt). Unlike [extractName], folder names aren't combined, so movies in a
     * collection like "Bruce Lee Collection/01. The Big Boss (1971).mp4" are named after their file.
     */
    fun extractMovieNameAndYear(file: File): Pair<String, Int?> {
        val rawBaseName = decodeIfEncoded(file.name).substringBeforeLast(".")
        val parsedFileName = parseMovieName(rawBaseName)
        // extras like "Movie (2010)/Featurettes/Interview.mkv" belong to the movie of the folder above
        val isInExtrasFolder = file.parentFile?.let { parent ->
            movieExtraPattern.containsMatchIn(decodeIfEncoded(parent.name)) && parent.parentFile?.let { !isLibraryGroupFolder(it) } == true
        } == true
        // and extras like "Movie (2010)/Movie Trailer.mkv" to the movie of their folder
        val isExtraInMovieFolder = movieExtraPattern.containsMatchIn(rawBaseName) && file.parentFile?.let { !isLibraryGroupFolder(it) } == true
        if (!isInExtrasFolder && !isExtraInMovieFolder && isMovieTitle(parsedFileName)) {
            return yearPrefixedTitle(file, parsedFileName) ?: parsedFileName
        }
        var current: File? = file.parentFile
        var depth = 0
        while (current != null && depth < 5 && !isLibraryGroupFolder(current)) {
            val parentName = decodeIfEncoded(current.name)
            if (parentName.lowercase() !in discStructureFolderNames && !(current == file.parentFile && isInExtrasFolder)) {
                val parsedParentName = parseMovieName(parentName)
                if (isMovieTitle(parsedParentName)) return parsedParentName
            }
            current = current.parentFile
            depth++
        }
        return Pair("", null)
    }

    /**
     * Splits a movie release name like "Alien.Romulus.2024.2160p.WEB-DL.DDP5.1" into title and year. Unlike [cleanName]
     * numbers are never taken for episodes, so "127 Hours" or "1917.2019" keep their title. The name is cut at the
     * release year, or without one at the first release tag. A cut inside parentheses drops the whole group, e.g.
     * "Zabriskie Point (Antonioni 1970)".
     */
    fun parseMovieName(rawName: String): Pair<String, Int?> {
        // "!" separates like "Werner - Beinhart!1990"
        var name = rawName.replace("：", " ").replace("?", " ").replace("!", "! ")
        name = name.replace(remoteMediaIdPattern, " ")
        name = name.replace(movieSitePrefixPattern, "")
        name = name.replace(leadingBracketGroupsPattern, "").trim()
        name = removeReleaseGroups(name)
        name = name.replace(listNumberPrefixPattern, "")
        name = name.replace("(", " (").replace(")", ") ").replace("[", " [").replace("]", "] ").replace("_", " ").replace("+", " ")
        name = name.replace(acronymPattern) { matchResult ->
            matchResult.value.replace(".", "")
        }
        name = name.replace(".", " ").replace(whitespacePattern, " ").trim()
        // dash separated names like "Fear.Eats.the.Soul-1974--720p-EN" or "maleficent-1080p", but not "Spider-Man"
        name = name.replace(tokenPattern) { token ->
            val parts = token.value.split('-')
            val isTaggedPart = parts.size > 1 && parts.any { it.lowercase().matches(movieTechnicalTagPattern) || it.matches(movieYearPattern) }
            if (isTaggedPart) parts.joinToString(" ") else token.value
        }.replace(whitespacePattern, " ").trim()

        val tokens = tokenPattern.findAll(name).toList()
        val cores = tokens.map { token -> token.value.trim { !it.isLetterOrDigit() }.lowercase() }
        fun isTag(index: Int, tagPattern: Regex) = cores[index].matches(tagPattern) || cores[index].split('-').any { it.matches(tagPattern) }
        fun isAnyTag(index: Int) = isTag(index, movieTechnicalTagPattern) || isTag(index, movieLanguageOrEditionTagPattern)

        // the first token is always part of the title, e.g. "1917" or "2012"
        val firstTechnicalTag = (1 until tokens.size).firstOrNull { isTag(it, movieTechnicalTagPattern) } ?: tokens.size
        val firstLanguageOrEditionTag = (1 until tokens.size).firstOrNull { isTag(it, movieLanguageOrEditionTagPattern) } ?: tokens.size
        val yearCandidates = (1 until firstTechnicalTag).filter { cores[it].matches(movieYearPattern) }
        // the release year is in parentheses, follows a year of the title or is followed by tags, otherwise it's the first
        // one ("Wonder Woman 1984 2020", "Summer of 1984 2023 1080p", "2000 Mules 2022 - ... of 2020")
        val yearIndex = yearCandidates.lastOrNull { index ->
            val token = tokens[index].value
            (token.startsWith("(") && token.endsWith(")")) || (token.startsWith("[") && token.endsWith("]")) ||
                    cores[index - 1].matches(movieYearPattern) || (index + 1 < tokens.size && isAnyTag(index + 1))
        } ?: yearCandidates.firstOrNull()
        val cutIndex = yearIndex ?: minOf(firstTechnicalTag, firstLanguageOrEditionTag)

        var cut = if (cutIndex < tokens.size) tokens[cutIndex].range.first else name.length
        val prefix = name.substring(0, cut)
        val openGroup = prefix.lastIndexOfAny(charArrayOf('(', '['))
        if (openGroup >= 0 && prefix.indexOfAny(charArrayOf(')', ']'), openGroup) == -1) {
            cut = openGroup
        }

        var title = name.substring(0, cut).replace(bracketPattern, " ").replace("(", " ").replace(")", " ")
            .replace(whitespacePattern, " ").trim()
        val trailingPunctuation = charArrayOf('-', '.', ',', ';', ':', '_', '~', '+', '!', '?', '/', '\\')
        while (true) {
            val before = title
            title = title.replace(trailingMovieTagPattern, "").trimEnd(*trailingPunctuation).trim()
            if (title == before) break
        }
        title = title.trimStart(*trailingPunctuation).trim()
        return Pair(title, yearIndex?.let { cores[it].toInt() })
    }

    /**
     * Collections like "The Texas Chainsaw Massacre Collection (1974-2017)/2017.Leatherface.mkv" put the year in front.
     */
    private fun yearPrefixedTitle(file: File, titleAndYear: Pair<String, Int?>): Pair<String, Int?>? {
        if (titleAndYear.second != null) return null
        val match = leadingYearTitlePattern.find(titleAndYear.first) ?: return null
        val year = match.groupValues[1].toInt()
        val isInCollectionOfThatTime = generateSequence(file.parentFile) { it.parentFile }.take(3).takeWhile { !isLibraryGroupFolder(it) }.any { folder ->
            yearRangePattern.findAll(decodeIfEncoded(folder.name)).any { range ->
                val from = range.groupValues[1].toInt()
                val to = range.groupValues[2].let { if (it.length == 2) from / 100 * 100 + it.toInt() else it.toInt() }
                year in from..to
            }
        }
        return if (isInCollectionOfThatTime) Pair(match.groupValues[2], year) else null
    }

    private fun isMovieTitle(titleAndYear: Pair<String, Int?>): Boolean {
        val (title, year) = titleAndYear
        if (title.isBlank() || title.none { it.isLetterOrDigit() } || title.lowercase().matches(movieTechnicalTagPattern)) return false
        // numeric titles like 300 or 1917 need a release year, "00001.m2ts" or "2022.mp4" aren't titles
        if (title.matches(pureNumberPattern)) return !title.startsWith("0") && year != null
        // with a release year short titles like "It (2017)", "Home (2015)" or "48.Hrs.1982" are titles as well
        if (year != null || !isJunk(title)) return true
        // isJunk takes "Cars 2" or "Saw 3" for a season or volume
        val normalized = title.lowercase()
        return normalized.matches(shortWordNumPattern) && !normalized.matches(seasonDiscPartPattern) && !normalized.matches(episodeLikeShortPattern)
    }

    /**
     * Letter, range (0-9) and category folders group the library, they never carry a title.
     */
    private fun isLibraryGroupFolder(folder: File): Boolean {
        val name = decodeIfEncoded(folder.name).trim()
        val lower = name.lowercase()
        val normalized = lower.replace("_", " ").replace(".", " ").replace("-", " ").trim()
        return name.length <= 1 || name.all { !it.isLetterOrDigit() } || lower.matches(rangeSinglePattern) || lower.matches(rangeLetterPattern) ||
                lower in categoryFolderNames || normalized in categoryFolderNames
    }

    /**
     * Series in the movies folder, detected by markers a movie title doesn't carry: S01E01 or 1x01 in the file name, a
     * season folder or several numbered episodes of the same show next to each other. A plain number like in
     * "Apollo 13" doesn't count.
     */
    fun hasSeriesMarkers(file: File): Boolean {
        val fileName = decodeIfEncoded(file.nameWithoutExtension)
        if (seasonEpisodeMarkerPattern.containsMatchIn(fileName) || seasonXEpisodeMarkerPattern.containsMatchIn(fileName)) return true

        // files directly in a letter folder, e.g. Star.Wars.Episode.1, have no show folder
        val showFolders = generateSequence(file.parentFile) { it.parentFile }.take(5).takeWhile { !isLibraryGroupFolder(it) }.toList()
        if (showFolders.isEmpty()) return false
        if (showFolders.any { seasonFolderMarkerPattern.containsMatchIn(decodeIfEncoded(it.name)) }) return true

        // e.g. "Tales.Of.Little.Women.EP1", "Samurai Champloo - Ep. 20 - Elegy", "Patlabor ON TELEVISION - 47 (BDRip)" or "Gunslinger Girl 01"
        val episodeMarker = episodeMarker(fileName) ?: return false
        val episodeMarkers = file.parentFile.listFiles()
            ?.filter { it.isFile && it.name != file.name && it.extension.lowercase() in videoExtensions }
            ?.mapNotNull { sibling -> episodeMarker(decodeIfEncoded(sibling.nameWithoutExtension))?.takeIf { it.showName == episodeMarker.showName } }
            .orEmpty()
            .plus(episodeMarker)
        // plain numbers only count zero-padded, "Ocean's 11", "Ocean's 12" and "Ocean's 13" are movies
        val hasEpisodeNotation = episodeMarkers.any { it.isExplicit || it.number.startsWith("0") }
        return hasEpisodeNotation && episodeMarkers.map { it.number.toInt() }.toSet().size >= 3
    }

    /**
     * Removes the episode number [extractName] keeps for plain numbered episodes, e.g. "Gunslinger Girl 10".
     */
    fun removeEpisodeNumber(seriesName: String, file: File): String {
        val episodeNumber = episodeMarker(decodeIfEncoded(file.nameWithoutExtension))?.number ?: return seriesName
        return seriesName.removeSuffix(" $episodeNumber").trim()
    }

    /**
     * Episode number in a file name, [isExplicit] if it's marked as episode (Ep. 20, - 47) rather than a plain trailing
     * number. [showName] is the normalized text in front of it.
     */
    private data class EpisodeMarker(val showName: String, val number: String, val isExplicit: Boolean)

    private fun episodeMarker(name: String): EpisodeMarker? {
        fun showName(text: String, end: Int) = text.substring(0, end).lowercase().replace(nonAlphanumericPattern, "")
        (explicitEpisodeLookbehindPattern.find(name) ?: dashEpisodePattern.find(name))?.let {
            return EpisodeMarker(showName(name, it.range.first), it.groupValues[1], true)
        }
        val withoutTags = name.replace(bracketPattern, " ").replace(versionPattern, "").trim()
        return trailingEpisodeNumberMarkerPattern.find(withoutTags)?.let { EpisodeMarker(showName(withoutTags, it.range.first), it.groupValues[1], false) }
    }

    /**
     * Extras folders inside a movie or show folder, like "Movie (2010)/Featurettes" or "Show/NC". The top folder itself
     * doesn't count, packs are named like "Good Omens Season 1 + Extras".
     */
    private fun isInExtrasFolder(file: File): Boolean {
        val folders = generateSequence(file.parentFile) { it.parentFile }.take(6).takeWhile { !isLibraryGroupFolder(it) }.toList()
        return folders.dropLast(1).any { folder ->
            val name = decodeIfEncoded(folder.name)
            movieExtraPattern.containsMatchIn(name) || creditlessFolderPattern.matches(name.trim())
        }
    }

    /**
     * Files left alone: parts of a disc structure (moving them breaks the disc) and video courses, which no media
     * database knows.
     */
    private fun getSkipReason(file: File): String? {
        val folderNames = generateSequence(file.parentFile) { it.parentFile }.map { decodeIfEncoded(it.name).lowercase() }.toList()
        return when {
            folderNames.any { it in discStructureRootNames } -> "disc structure"
            paddingFilePattern.containsMatchIn(decodeIfEncoded(file.name)) -> "padding file"
            file.extension.lowercase() in nonMediaExtensions -> "no media file"
            coursePattern.containsMatchIn(decodeIfEncoded(file.absolutePath)) -> "course"
            else -> null
        }
    }

    fun extractYear(fullName: String): Int? {
        val file = File(fullName)

        // 1. Prefer explicit parenthesized year in file name
        val nameParenMatch = parenthesizedYearExtractorRegex.find(file.name)
        if (nameParenMatch != null) return nameParenMatch.groupValues[1].toInt()

        val parent = file.parentFile
        val isSeasonFolder = parent?.name?.let {
            seasonFolderPattern.containsMatchIn(it)
        } ?: false

        // If parent is a season folder, check grandparent show folder first
        if (isSeasonFolder && parent.parentFile != null) {
            val grandParentParenMatch = parenthesizedYearExtractorRegex.find(parent.parentFile.name)
            if (grandParentParenMatch != null) return grandParentParenMatch.groupValues[1].toInt()

            val grandParentMatch = yearExtractorRegex.find(parent.parentFile.name)
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
            val parentParenMatch = parenthesizedYearExtractorRegex.find(current.name)
            if (parentParenMatch != null) return parentParenMatch.groupValues[1].toInt()
            current = current.parentFile
            depth++
        }

        // 3. Fall back to unparenthesized year (prefer match where index > 0, a leading year like 1917.2019 is the title)
        val nameMatches = yearExtractorRegex.findAll(file.name).toList()
        nameMatches.firstOrNull { it.range.first > 0 }?.let { return it.groupValues[1].toInt() }

        current = parent
        depth = 0
        while (current != null && depth < 5) {
            val currMatch = yearExtractorRegex.findAll(current.name).firstOrNull { it.range.first > 0 }
            if (currMatch != null) {
                return currMatch.groupValues[1].toInt()
            }
            current = current.parentFile
            depth++
        }

        nameMatches.firstOrNull()?.let { return it.groupValues[1].toInt() }

        current = parent
        depth = 0
        while (current != null && depth < 5) {
            val currMatch = yearExtractorRegex.find(current.name)
            if (currMatch != null) {
                return currMatch.groupValues[1].toInt()
            }
            current = current.parentFile
            depth++
        }

        return null
    }

    private fun containerName(folderName: String) =
        folderName.lowercase().replace(bracketPattern, " ").replace("_", " ").replace(".", " ").substringAfterLast(" - ")
            .replace(whitespacePattern, " ").trim()

    private fun isExtrasFolder(folderName: String) = containerName(folderName).matches(extrasFolderPattern)

    private fun isExtrasContainer(folderName: String) = containerName(folderName).let { it.matches(extrasFolderPattern) || it.matches(subtitleFolderPattern) }

    /**
     * Name of an extra in the featurettes of its show, with the folders below the outermost extras folder so extras of
     * different folders don't collide, e.g. "Season 2 - The Log Lady - The Log Lady 01.mkv" for
     * "Twin.Peaks.(1990)/Featurettes/Season 2/The Log Lady/The Log Lady 01.mkv". Null if the file isn't an extra.
     */
    fun extraNameInFeaturettes(file: File): String? {
        val folders = generateSequence(file.parentFile) { it.parentFile }.takeWhile { !isLibraryGroupFolder(it) }.toList()
        val outermostExtrasFolder = folders.indexOfLast { isExtrasFolder(decodeIfEncoded(it.name)) }
        // extras named after their folder like "Season 03 Extras - Inside The X-Files.avi"
        val fileName = decodeIfEncoded(file.nameWithoutExtension)
        if (outermostExtrasFolder < 0 && fileName.contains(" - ") && isExtrasFolder(fileName.substringBefore(" - "))) return decodeIfEncoded(file.name)
        if (outermostExtrasFolder < 0) return null
        val subFolders = folders.take(outermostExtrasFolder).reversed().map { decodeIfEncoded(it.name) }
        return (subFolders + decodeIfEncoded(file.name)).joinToString(" - ")
    }

    /**
     * Language of a subtitle from its name or folder, e.g. "en" for "2_English.srt", "da" for "Danish/Show E1.srt" or
     * "en.sdh" for "english_SDH/Show.S01E01.srt".
     */
    fun extractSeriesSubtitleLanguage(file: File): String? {
        extractSubtitleLanguage(file)?.let { return it }
        val tokens = (listOf(file.nameWithoutExtension) + listOfNotNull(file.parentFile?.name))
            .flatMap { decodeIfEncoded(it).lowercase().split(nonLetterPattern) }.filter { it.isNotBlank() }
        val language = tokens.firstNotNullOfOrNull { languageNames[it] } ?: return null
        return (listOf(language) + subtitleFlags.filter { it in tokens }).joinToString(".")
    }

    /**
     * Movies in a series folder go to the movies, detected by a movie in the file name or in one of the two folders
     * above, e.g. "Naruto/3.Naruto Movies/1 - Ninja Clash in the Land of Snow.mkv" or "Aeon Flux/Movie (2005)/Aeon Flux.mp4".
     * Packs of episodes and movies like "Eden of the East 1-11+Movie 1-2 Complete" don't make their episodes movies.
     */
    fun isMovieInSeriesFolder(file: File): Boolean {
        val fileName = decodeIfEncoded(file.nameWithoutExtension)
        if (isOpeningEndingOrOva(file) || pilotPattern.containsMatchIn(fileName) || isMovieExtra(file)) return false
        if (seasonEpisodeMarkerPattern.containsMatchIn(fileName) || seasonXEpisodeMarkerPattern.containsMatchIn(fileName) ||
            seasonInNamePattern.containsMatchIn(fileName)
        ) return false
        // files in a show of the library were put there on purpose, e.g. "The Office (US) {tvdb-73244}/Season 7/Threat Level Midnight The Movie.mkv"
        if (remoteMediaIdPattern.containsMatchIn(decodeIfEncoded(file.absolutePath))) return false
        val folders = generateSequence(file.parentFile) { it.parentFile }.takeWhile { !isLibraryGroupFolder(it) }.map { decodeIfEncoded(it.name) }.toList()
        if (folders.any { isExtrasContainer(it) }) return false
        return movieFileWordPattern.containsMatchIn(fileName) ||
                folders.take(2).any { movieFolderWordPattern.containsMatchIn(it) && !episodesAndMoviesPackPattern.containsMatchIn(it) }
    }

    /**
     * Title and year of a movie in a series folder without its numbering, "Hunter X Hunter - Movie 1 - Phantom Rouge"
     * is searched as "Hunter X Hunter - Phantom Rouge", the year may come from its folder like "Movie (2005)".
     */
    fun extractMovieInSeriesNameAndYear(file: File): Pair<String, Int?> {
        val fileName = decodeIfEncoded(file.nameWithoutExtension).replace(leadingTrackNumberPattern, "")
        val (name, year) = parseMovieName(fileName)
        val subtitleMatch = movieSubtitlePattern.find(fileName.replace(".", " ").replace("_", " "))
        val fullName = if (subtitleMatch != null) {
            // the year stands before the number, "Ranma ½ [1991] - Movie 01 - Big Trouble In Nekonron China" would end at it
            val show = cleanName(subtitleMatch.groupValues[1])
            "$show ${parseMovieName(subtitleMatch.groupValues[2]).first}"
        } else name
        val title = fullName.replace(movieNumberPattern, " - ").replace(repeatedDashPattern, " - ")
            .replace(whitespacePattern, " ").trim { it == ' ' || it == '-' }
        val folderYear = file.parentFile?.let { parseMovieName(decodeIfEncoded(it.name)) }?.second
        return Pair(title, year ?: folderYear)
    }

    /**
     * Position of an opening, ending or OVA marker like "NCOP 01", "- ED1" or "OVA 02", ignoring hashes like [ED8CC609].
     */
    private fun specialMarkerIndex(name: String, casingReference: String = name): Int? {
        val nameWithoutBrackets = name.replace(bracketPattern) { " ".repeat(it.value.length) }
        val ovaMatch = ovaMarkerPattern.find(nameWithoutBrackets)
        // an all upper case name like "ED.EDD.N.EDDY" has no distinguishable OP or ED markers
        val hasLowerCase = casingReference.replace(bracketPattern, " ").any { it.isLowerCase() }
        val openingEndingMatch = if (hasLowerCase) openingEndingMarkerPattern.find(nameWithoutBrackets) else null
        return listOfNotNull(ovaMatch?.range?.first, openingEndingMatch?.range?.first).minOrNull()
    }

    /**
     * Openings, endings and OVAs are stored as "Other" extras of the show, Plex lists them there by file name. As specials
     * they'd need the special's number of TVDB, their own numbering would collide otherwise ("NCOP 01" and "OVA 01").
     */
    fun isOpeningEndingOrOva(file: File) = specialMarkerIndex(decodeIfEncoded(file.nameWithoutExtension)) != null

    fun extractSeason(file: File): Int? {
        if (isOpeningEndingOrOva(file) || airDatePattern.containsMatchIn(decodeIfEncoded(file.name))) return null
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

    private fun bracketedSeasonEpisode(fileName: String): Pair<Int, Int>? {
        val match = bracketedEpisodePattern.find(fileName) ?: return null
        val (season, episode) = match.groupValues.drop(1).filter { it.isNotEmpty() }.map { it.toInt() }
        return Pair(season, episode)
    }

    fun extractDirectSeason(file: File): Int? {
        bracketedSeasonEpisode(decodeIfEncoded(file.name))?.let { return it.first }
        doubleDashEpisodePattern.find(decodeIfEncoded(file.name))?.let { return it.groupValues[1].toInt() }
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

        // Check yearEpisodeDirectPattern, seasonPattern (e.g. S01), seasonDirectPatternInName (e.g. Season 1), dvdDirectPattern
        current = file
        depth = 0
        while (current != null && depth < 6) {
            val name = decodeIfEncoded(current.name)
            val match = yearEpisodeDirectPattern.find(name)
                ?: seasonPattern.find(name)
                ?: seasonDirectPatternInName.find(name)
                ?: dvdDirectPattern.find(name)
            if (match != null) {
                val groupVal = match.groupValues.drop(1).firstOrNull { it.isNotEmpty() } ?: match.groupValues.last()
                return groupVal.toInt()
            }
            current = current.parentFile
            depth++
        }

        return null
    }

    fun extractEpisode(file: File, mediaItem: MediaItem? = null): Int? {
        return extractEpisodeAndImpliedSeason(file, mediaItem)?.first
    }

    /**
     * Episode number plus the season implied by its notation (1x05, 0105 or a disambiguated 305), if any.
     */
    private fun extractEpisodeAndImpliedSeason(file: File, mediaItem: MediaItem?): Pair<Int, Int?>? {
        if (isOpeningEndingOrOva(file)) return null
        val fileName = decodeIfEncoded(file.name)
        val parentName = decodeIfEncoded(file.parentFile?.name ?: "")
        val grandParentName = decodeIfEncoded(file.parentFile?.parentFile?.name ?: "")
        val greatGrandParentName = decodeIfEncoded(file.parentFile?.parentFile?.parentFile?.name ?: "")

        val match2 = seasonPattern2.find(fileName)
            ?: seasonPattern2.find(parentName)
            ?: seasonPattern2.find(grandParentName)
            ?: seasonPattern2.find(greatGrandParentName)

        if (match2 != null) {
            return Pair(match2.groupValues[2].toInt(), match2.groupValues[1].toInt())
        }

        gluedSeasonEpisode(file)?.let { (_, season, episode) -> return Pair(episode, season) }
        // specials of year seasons like "MythBusters 2008X01 Shark Week Special 2" have no regular episode number
        if (yearSpecialPattern.containsMatchIn(fileName) || episodeSpecialPattern.containsMatchIn(fileName) || airDatePattern.containsMatchIn(fileName)) return null
        explicitSeasonEpisodePattern.find(fileName)?.let { return Pair(it.groupValues[2].toInt(), it.groupValues[1].toInt()) }
        doubleDashEpisodePattern.find(fileName)?.let { return Pair(it.groupValues[2].toInt(), it.groupValues[1].toInt()) }
        if (episodeRangeWordPattern.containsMatchIn(fileName)) return null
        // a bracketed episode wins over numbers in the title like "The Simpsons [20.19] Waverly Hills 9-0-2-1-D'oh"
        bracketedSeasonEpisode(fileName)?.let { (season, episode) -> return Pair(episode, season) }
        partOfPattern.find(fileName)?.let { return Pair(it.groupValues[1].toInt(), null) }
        // the number after the show, not the one in the episode's title like "No Game No Life 12 Rule Number 10"
        episodeAfterShowPattern.find(decodeIfEncoded(file.nameWithoutExtension))?.let { return Pair(it.groupValues[1].toInt(), null) }

        val rawEpisodeInfo = findRawEpisodeMatch(file) ?: return null
        val (rawStr, num) = rawEpisodeInfo

        val directSeason = extractDirectSeason(file)
        if (num in 101..9999) {
            if (rawStr.length == 4 && rawStr.startsWith("0")) {
                val s = rawStr.substring(0, 2).toInt()
                val e = rawStr.substring(2, 4).toInt()
                if (directSeason == null || directSeason == s) {
                    return Pair(e, s)
                }
            }
            if (!rawStr.startsWith("0") && shouldDisambiguateAsSeasonEpisode(file, directSeason, num, rawStr, mediaItem)) {
                return Pair(num % 100, num / 100)
            }
        }

        return Pair(num, null)
    }

    /**
     * Show, season and episode of a file name like "Shameless901.Are You There Shim", only if a folder confirms the season.
     */
    private fun gluedSeasonEpisode(file: File): Triple<String, Int, Int>? {
        val match = gluedSeasonEpisodePattern.find(decodeIfEncoded(file.nameWithoutExtension)) ?: return null
        val season = match.groupValues[2].toInt()
        val episode = match.groupValues[3].toInt()
        if (episode == 0 || extractDirectSeason(file) != season) return null
        return Triple(match.groupValues[1], season, episode)
    }

    private fun findRawEpisodeMatch(file: File): Pair<String, Int>? {
        val fileName = decodeIfEncoded(file.name).replace(audioChannelsPattern, " ").replace(decimalNumberPattern, " ")
        val parentName = decodeIfEncoded(file.parentFile?.name ?: "")
        val grandParentName = decodeIfEncoded(file.parentFile?.parentFile?.name ?: "")
        val greatGrandParentName = decodeIfEncoded(file.parentFile?.parentFile?.parentFile?.name ?: "")

        val excludedAcronyms = setOf(
            "x264", "h264", "x265", "h265", "hevc", "avc", "xvid", "dvd", "disc", "cd", "bd", "eac", "eac3", "ddp", "dd",
            "part", "pt", "vol", "v", "ch", "aac", "mp3", "mp4", "ac3", "fps", "kbps", "mbps",
            "s", "se", "season", "dvdrip", "bdrip", "brrip", "webrip", "webdl", "hdtv", "pdtv"
        )
        val replaced = fileName.replace(bracketPattern, " ").replace(versionPattern, "").replace("_", " ")

        val rawBaseName = fileName.substringBeforeLast(".").replace(bracketPattern, " ").replace(versionPattern, "").trim()
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
            .flatMap { it.split(nonAlphanumericPattern) }
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
            ?: explicitEpisodeLookbehindPattern.find(fileName)
            ?: episodeWithTitlePattern.find(fileName)
            ?: episodeWithTitlePattern.find(replaced)
            ?: dashEpisodePattern.find(fileName)
            ?: dashEpisodePattern.find(replaced)
            ?: dashTrailingNumberPattern.find(fileName)
            ?: acronymMatch
            ?: zeroPaddedSeasonEpisodePattern.find(fileName)
            ?: standaloneEpisodePattern.find(fileName)?.let { if (fourDigitYearPattern.find(it.value) == null) it else null }
            ?: firstValidEpisodeMatch
            ?: explicitEpisodeLookbehindPattern.find(parentName)
            ?: explicitEpisodeLookbehindPattern.find(grandParentName)

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
                zeroPadded3DigitsPattern.containsMatchIn(sName)
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

    /**
     * Renames and moves the media files below [path] into the library at [basePath] (default ~/pfdb/PFDB).
     */
    fun processFilesFromDirectory(path: String, logFile: File? = null, isTestRun: Boolean = true, basePath: String? = null) {
        val writer = logFile?.let { BufferedWriter(FileWriter(it, true)) }
        try {
            processFilesFromDirectoryInternal(path, writer, isTestRun, basePath, HashSet())
        } finally {
            writer?.flush()
            writer?.close()
            mediaProxyService.saveCache()
        }
    }

    /**
     * [assignedTargets] are the targets of this run, a test run reports the files which would collide as well.
     */
    private fun processFilesFromDirectoryInternal(
        path: String, logWriter: BufferedWriter?, isTestRun: Boolean, basePath: String?, assignedTargets: MutableSet<String>
    ) {
        val files = File(path).listFiles()
        files?.let { fileList ->
            for (file in fileList) {
                if (file.isFile) {
                    val fullName = file.absolutePath
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

                    // the folder wins over season/episode detection, titles like "Apollo 13" would be detected as episode,
                    // only explicit series markers move a file from the movies folder to the series
                    val isSeriesInMoviePath = isMoviePath && !isSeriesPath && hasSeriesMarkers(file)
                    val isMovieInSeriesPath = isSeriesPath && !isMoviePath && isMovieInSeriesFolder(file)
                    val mediaIndicator = when {
                        isMovieInSeriesPath -> MediaType.Movie
                        isSeriesPath || isSeriesInMoviePath -> MediaType.Series
                        isMoviePath -> MediaType.Movie
                        episode != null || season != null -> MediaType.Series
                        else -> null
                    }

                    // extract Movie or TV show name from full path
                    val (extractedName, year) = if (isMovieInSeriesPath) {
                        extractMovieInSeriesNameAndYear(file)
                    } else if (mediaIndicator == MediaType.Movie) {
                        // a year from the future is part of a hash like [6D2027D6]
                        val currentYear = java.time.Year.now().value
                        extractMovieNameAndYear(file).let { (name, movieYear) -> Pair(name, movieYear ?: extractYear(fullName)?.takeIf { it <= currentYear }) }
                    } else {
                        val seriesName = if (isSeriesInMoviePath) removeEpisodeNumber(extractName(file), file) else extractName(file)
                        Pair(seriesName, extractYear(fullName))
                    }

                    val skipReason = getSkipReason(file)
                        ?: if (isMoviePath && isInExtrasFolder(file)) "extras" else if (mediaIndicator == MediaType.Movie && !isMovieFile(file)) "no movie file" else null
                    if (skipReason != null) {
                        val logLine = "F:'$fullName'→'$extractedName'→'null' (Y: $year, S: $season, E: $episode)  skipped, $skipReason"
                        logWriter?.write(logLine + "\n")
                        continue
                    }

                    // already renamed files carry their remote media id, the one closest to the file wins
                    val remoteMediaId = remoteMediaIdPattern.findAll(fullName).lastOrNull()?.value
                    val mediaResult = remoteMediaId?.let { mediaProxyService.findByRemoteMediaId(it, extractedName, mediaIndicator) }
                        ?: if (isMovieInSeriesPath && year == null) searchMovieByTitle(extractedName)
                        else searchMediaForFile(extractedName, year, mediaIndicator, (isSeriesPath && !isMovieInSeriesPath) || isSeriesInMoviePath)

                    if (mediaResult != null) {
                        val seasonAndEpisode = extractSeasonAndEpisode(file, mediaResult)
                        season = seasonAndEpisode.first
                        episode = seasonAndEpisode.second
                    }

                    val idString = mediaResult?.id ?: ""
                    val isSkippedMovieFile = mediaResult?.type == MediaType.Movie && !isMovieFile(file)
                    val logLine =
                        "F:'$fullName'→'$extractedName'→'$mediaResult' (Y: $year, S: $season, E: $episode) $idString" +
                                if (isSkippedMovieFile) " skipped, no movie file" else ""
                    mediaResult?.takeUnless { isSkippedMovieFile }?.let {
                        val targetFile = getTargetFile(file, mediaResult, extractedName, season, episode, basePath)
                        // actual move
                        // Create the parent directories if they don't exist
                        val newFile = File(targetFile)
                        val isSameFile = file.toPath().equals(newFile.toPath())
                        if (isSameFile) assignedTargets.add(newFile.absolutePath)
                        // nothing is ever overwritten, neither a file of the library nor one moved before in this run, e.g.
                        // two editions of a movie, two releases of an episode or the same episode number from two notations
                        val isTargetTaken = !isSameFile && (newFile.exists() || !assignedTargets.add(newFile.absolutePath))
                        if (isTargetTaken) {
                            logWriter?.write("Not moved, target exists:\n'$file'\n'$newFile'\n")
                        } else if(!isTestRun && !isSameFile) {
                            newFile.parentFile?.mkdirs()
                            // Then move and rename
                            try {
                                file.toPath().moveTo(newFile.toPath(), overwrite = false)
                                logWriter?.write("Moved:\n'$file'\n'$newFile'\n")
                            } catch (e:Exception) {
                                println("Error moving file: $e")
                                logWriter?.write("Failed to move:\n'$file'\n'$newFile'\n")
                            }

                        }
                    }
                    logWriter?.write(logLine + "\n")
                } else {
                    processFilesFromDirectoryInternal(file.absolutePath, logWriter, isTestRun, basePath, assignedTargets)
                }
            }
        }
    }

    private fun searchMediaForFile(extractedName: String, year: Int?, mediaIndicator: MediaType?, isSeriesPath: Boolean): MediaItem? {
        if (extractedName.isBlank()) return null
        if (mediaIndicator == MediaType.Movie && year != null) return searchMovie(extractedName, year)
        val primaryResult = if (year != null) {
            val res = mediaProxyService.search(extractedName, year, mediaIndicator).firstOrNull()
            res ?: mediaProxyService.search(extractedName, mediaIndicator).firstOrNull()
        } else {
            mediaProxyService.search(extractedName, mediaIndicator).firstOrNull()
        }

        return if (year != null && (primaryResult == null || (mediaIndicator == MediaType.Series && primaryResult.type != MediaType.Series) || (isSeriesPath && primaryResult.type != MediaType.Series))) {
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
    }

    /**
     * An exact title released within a year wins over other matches of the year search, e.g. "Fargo" (1996) for
     * Fargo.1995 instead of "Danzig: Live at Fargo" (1995). A movie released years apart is another movie with the same
     * name (Air 2015 vs. Air 2023), then the file is better left alone.
     */
    private fun searchMovie(name: String, year: Int): MediaItem? {
        val yearResults = mediaProxyService.search(name, year, MediaType.Movie)
        val nameResults = mediaProxyService.search(name, MediaType.Movie)
        val normalizedName = normalizeMovieTitle(name)
        fun isReleasedNear(item: MediaItem, years: Int) = item.year?.let { abs(it - year) <= years } == true
        fun hasSameTitle(item: MediaItem) = listOfNotNull(item.title, item.originalTitle).any { normalizeMovieTitle(it) == normalizedName }
        return (yearResults + nameResults).firstOrNull { it.type == MediaType.Movie && isReleasedNear(it, 1) && hasSameTitle(it) }
            ?: yearResults.firstOrNull { it.type == MediaType.Movie && isReleasedNear(it, 2) }
            ?: nameResults.firstOrNull { it.type == MediaType.Movie && isReleasedNear(it, 2) }
            // titles containing a year, e.g. "Blade Runner 2049"
            ?: mediaProxyService.search("$name $year", MediaType.Movie).firstOrNull { it.title.contains(year.toString()) }
    }

    /**
     * Movie without a year, the first result only counts if it shares most words with the title, a series folder often
     * carries more than the movie's name.
     */
    private fun searchMovieByTitle(name: String): MediaItem? {
        val words = significantWords(name)
        // a single word like "Shippuden" or "dar2" matches too many movies
        if (words.size < 2) return null
        return mediaProxyService.search(name, MediaType.Movie).firstOrNull { item ->
            item.type == MediaType.Movie && listOfNotNull(item.title, item.originalTitle).any { title ->
                val titleWords = significantWords(title)
                words.count { it in titleWords } * 2 >= words.size
            }
        }
    }

    private fun significantWords(title: String) =
        title.lowercase().split(nonAlphanumericPattern).filter { it.length >= 3 && it !in titleStopWords }.toSet()

    private fun normalizeMovieTitle(title: String) = MediaProxyService.normalizeTitle(title).removePrefix("the ").replace(" ", "")

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

    /**
     * Detects season and episode of a file belonging to [mediaItem]. Movies have neither. For series without an
     * explicit season, the season implied by the episode number (e.g. 305 -> S03E05) is used, otherwise season 1.
     */
    fun extractSeasonAndEpisode(file: File, mediaItem: MediaItem): Pair<Int?, Int?> {
        if (mediaItem.type == MediaType.Movie) return Pair(null, null)
        val episodeInfo = extractEpisodeAndImpliedSeason(file, mediaItem)
        val episode = episodeInfo?.first
        var season = extractSeason(file)
        if (season == null && episode != null && mediaItem.type == MediaType.Series) {
            season = episodeInfo.second ?: 1
        }
        return Pair(season, episode)
    }

    fun getTargetFile(
        file: File,
        mediaResult: MediaItem,
        extractedName: String,
        season: Int?,
        episode: Int?,
        basePath: String? = null
    ): String {
        val base = basePath ?: (System.getProperty("user.home") + "/pfdb/PFDB")
        val idPart = if (!mediaResult.id.isNullOrBlank()) " ${mediaResult.id}" else ""
        if (mediaResult.type == MediaType.Movie) {
            return getMovieTargetFile(file, mediaResult, base, idPart)
        }
        val firstPart = base + "/" + getMediaTypeFolderString(mediaResult) +
                "/" + StringUtil.deductFirstTorrentLetter(getSafeTitle(mediaResult)) + "/${getSafeTitle(mediaResult)}$idPart"
        if (isOpeningEndingOrOva(file)) {
            return "$firstPart/$otherExtrasFolder/${file.name}"
        }
        extraNameInFeaturettes(file)?.let { return "$firstPart/$featurettesFolder/$it" }
        val optionalSeasonFolder = if(season != null){
            "/Season $season"
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
            // subtitles of several languages for the same episode
            val languageSuffix = if (file.extension.lowercase() in subtitleExtensions) extractSeriesSubtitleLanguage(file)?.let { ".$it" } ?: "" else ""
            val partSuffix = episodePartPattern.find(decodeIfEncoded(file.nameWithoutExtension))?.groupValues?.get(1)?.lowercase()
                ?: doubleEpisodePattern.find(decodeIfEncoded(file.nameWithoutExtension))
                    ?.takeIf { it.groupValues[1].toInt() == episode && it.groupValues[2].toInt() == episode + 1 }
                    ?.let { "-E" + String.format("%02d", episode + 1) }
                ?: ""
            // a title like "hak5" in "Hak5 {tvdb-79008}" counts as junk, the name must not start with a dot
            val episodeTitle = extractedName.ifBlank { getSafeTitle(mediaResult) }
            "/${episodeTitle}.S${seasonStr}E${episodeStr}$partSuffix$extraSuffix$languageSuffix"
        } else if (season != null || mediaResult.type == MediaType.Series) {
            "/${file.nameWithoutExtension}"
        } else {
            ""
        }
        val fileExtension = file.name.replace(file.nameWithoutExtension, "")
        return firstPart + optionalSeasonFolder+optionalFileNaming+fileExtension
    }

    /**
     * Movies are stored flat in their letter folder as "Title (Year) {id}.resolution.codec.ext". Subtitles with a
     * language code follow the movie name, extras/samples keep their original name so they don't overwrite the movie.
     */
    private fun getMovieTargetFile(file: File, mediaResult: MediaItem, base: String, idPart: String): String {
        val safeTitle = getSafeTitle(mediaResult)
        val folder = base + "/" + getMediaTypeFolderString(mediaResult) + "/" + StringUtil.deductFirstTorrentLetter(safeTitle)
        val extension = file.extension.lowercase()
        val isSubtitle = extension in subtitleExtensions
        val subtitleLanguage = if (isSubtitle) extractSubtitleLanguage(file) else null
        if (isMovieExtra(file) || (isSubtitle && subtitleLanguage == null)) {
            return "$folder/${file.name}"
        }
        val yearPart = mediaResult.year?.let { " ($it)" } ?: ""
        val mediaInfo = listOfNotNull(extractResolution(file), extractCodec(file))
        val mediaInfoSuffix = if (mediaInfo.isNotEmpty()) "." + mediaInfo.joinToString(".") else ""
        val partSuffix = moviePartPattern.find(file.nameWithoutExtension)?.let { " - pt${it.groupValues[1].toInt()}" } ?: ""
        val languageSuffix = subtitleLanguage?.let { ".$it" } ?: ""
        val fileExtension = if (file.extension.isNotEmpty()) ".${file.extension}" else ""
        return "$folder/$safeTitle$yearPart$idPart$mediaInfoSuffix$partSuffix$languageSuffix$fileExtension"
    }

    private fun isMovieExtra(file: File): Boolean {
        return movieExtraPattern.containsMatchIn(decodeIfEncoded(file.nameWithoutExtension)) ||
                file.parentFile?.let { movieExtraPattern.containsMatchIn(decodeIfEncoded(it.name)) } == true
    }

    private fun extractSubtitleLanguage(file: File): String? {
        val parts = file.nameWithoutExtension.lowercase().split('.')
        // e.g. movie.en.srt, movie.eng.forced.srt
        val flags = setOf("forced", "sdh", "cc")
        val languageIndex = parts.indexOfLast { it !in flags }
        if (languageIndex <= 0) return null
        val language = parts[languageIndex]
        if (language.length !in 2..3 || language !in languageCodes) return null
        return (listOf(language) + parts.drop(languageIndex + 1)).joinToString(".")
    }

    fun getSafeTitle(mediaResult: MediaItem): String {
        return getSafeTitle(mediaResult.title)
    }

    fun getSafeTitle(title: String): String {
        // a leading dot like in ".hack//G.U. Trilogy" would make a hidden file
        return title.replace("//", " ").replace(invalidFileCharsPattern, "").trimStart('.', ' ')
    }

    private fun getMediaTypeFolderString(mediaResult: MediaItem): String = when (mediaResult.type) {
        MediaType.Movie -> "Movies"
        MediaType.Series -> "Series-Shows"
        MediaType.Documentary -> "Documentaries"
        else -> "transfer"
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
        return decodedName.replace(letterOSeasonPattern, "S0")
    }
}