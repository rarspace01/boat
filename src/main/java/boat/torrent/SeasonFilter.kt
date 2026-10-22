package boat.torrent

/**
 * Tells from the name of a torrent if it has a season of a series: "Show S02E05", "Show S02", "Show Season 2",
 * "Show 2x05", the packs of several seasons ("S01-S05", "Seasons 1-3") and complete series. Names which say nothing
 * about a season don't match.
 */
object SeasonFilter {
    private const val MAX_RANGE = 60

    private val seasonRange = "(?<![a-z0-9])s(\\d{1,2})(?:e\\d{1,3})?(?:\\s*(?:-|–|to|&|and)\\s*s?(\\d{1,2})(?![0-9e]))?(?![0-9])".toRegex(RegexOption.IGNORE_CASE)
    private val seasonWord = "(?<![a-z0-9])seasons?[\\s._-]*(\\d{1,2})(?:[\\s._-]*(?:-|–|to|&|and|\\+)[\\s._-]*(?:seasons?[\\s._-]*)?(\\d{1,2})(?![0-9]))?(?![0-9])".toRegex(RegexOption.IGNORE_CASE)
    private val seasonXEpisode = "(?<![a-z0-9])(\\d{1,2})x\\d{2,3}(?![0-9])".toRegex(RegexOption.IGNORE_CASE)
    private val completeSeries = "(?<![a-z0-9])(?:complete[\\s._-]*(?:series|collection|seasons?|show)|all[\\s._-]*seasons|full[\\s._-]*series|box[\\s._-]*set|boxset)(?![a-z0-9])".toRegex(RegexOption.IGNORE_CASE)

    fun matches(torrentName: String, season: Int): Boolean {
        val name = torrentName.replace('.', ' ').replace('_', ' ')
        val seasons = mutableSetOf<Int>()
        listOf(seasonRange, seasonWord).forEach { pattern ->
            pattern.findAll(name).forEach { match ->
                val first = match.groupValues[1].toInt()
                val last = match.groupValues[2].toIntOrNull()
                if (last != null && last > first && last - first < MAX_RANGE) seasons.addAll(first..last) else seasons.add(first)
            }
        }
        seasonXEpisode.findAll(name).forEach { seasons.add(it.groupValues[1].toInt()) }
        return if (seasons.isNotEmpty()) season in seasons else completeSeries.containsMatchIn(name)
    }
}
