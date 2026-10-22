package boat.rename

import boat.info.MediaItem
import boat.info.MediaProxyService
import boat.info.MediaType
import boat.rename.EpisodeParser.nonAlphanumericPattern
import boat.rename.MediaNameParser.normalizeMovieTitle
import kotlin.math.abs

/**
 * Finds the movie in the media proxy that belongs to a file name: exact title and year, shortened titles, spelling variants, titles without year.
 */
internal class MovieMatcher(private val mediaProxyService: MediaProxyService) {

    companion object {
        private val titleConnectors = setOf("the", "and", "of", "a", "an", "in", "on", "to", "or", "at", "for", "with", "vs", "de", "la", "le", "el", "der", "die", "das")

        val titleStopWords = setOf("the", "and", "of", "a", "an", "in", "on", "to", "der", "die", "das")

        private const val minTitleSimilarity = 0.88

        private const val maxTrimmedWords = 4

        private val romanNumerals = listOf("i", "ii", "iii", "iv", "v", "vi", "vii", "viii", "ix", "x").withIndex().associate { it.value to (it.index + 1).toString() }

        private val titleNumberPattern = "[0-9]+|[ivx]+".toRegex()

        // "Predator 1 BD", "The Hangover Part I" or "Letters From Iwo Jima KLAXXON", noise behind a title found without a year
        val trailingMovieNoisePattern = "(?:(?i:\\s+part)?(?i:\\s+(?:1|i))|(?i:\\s+(?:bd|hdmux|dvdmux))|\\s+[A-Z]{6,})$".toRegex()

        val akaPattern = "(?i)\\s+(?:a\\.?k\\.?a\\.?|aka)\\s+".toRegex()
    }

    fun searchMediaForFile(extractedName: String, year: Int?, mediaIndicator: MediaType?, isSeriesPath: Boolean): MediaItem? {
        if (extractedName.isBlank()) return null
        if (mediaIndicator == MediaType.Movie && year != null) return searchMovie(extractedName, year)
        if (mediaIndicator == MediaType.Movie) return searchMovieWithoutYear(extractedName)
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
        return searchMovieExact(name, year) ?: searchMovieByShortenedTitle(name, year)
    }

    private fun searchMovieExact(name: String, year: Int): MediaItem? {
        // "Aqua-Man" is "Aquaman" as well
        val queries = if (name.contains('-')) listOf(name, name.replace("-", ""), name.replace("-", " ")).distinct() else listOf(name)
        val yearResults = queries.flatMap { mediaProxyService.search(it, year, MediaType.Movie) }
        val nameResults = queries.flatMap { mediaProxyService.search(it, MediaType.Movie) }
        val normalizedName = normalizeMovieTitle(name)
        fun isReleasedNear(item: MediaItem, years: Int) = item.year?.let { abs(it - year) <= years } == true
        fun hasSameTitle(item: MediaItem) = listOfNotNull(item.title, item.originalTitle).any { normalizeMovieTitle(it) == normalizedName }
        val results = (yearResults + nameResults).filter { it.type == MediaType.Movie }
        // a different title is only trusted in the release year itself, "Harley Quinn" (2019) is not "Batman and Harley Quinn" (2017)
        return results.firstOrNull { isReleasedNear(it, 1) && hasSameTitle(it) }
            // the original title of "Shocking Dark" is "Terminator 2", it is not the Terminator 2 of 1991
            ?: results.firstOrNull { isReleasedNear(it, 2) && normalizeMovieTitle(it.title) == normalizedName }
            ?: yearResults.firstOrNull { it.type == MediaType.Movie && isReleasedNear(it, 1) }
            // titles containing a year, e.g. "Blade Runner 2049", but not "Algérie 1988-2000" for "Dune" (2000)
            ?: mediaProxyService.search("$name $year", MediaType.Movie).firstOrNull {
                it.type == MediaType.Movie && listOfNotNull(it.title, it.originalTitle).any { title -> normalizeMovieTitle(title) == normalizeMovieTitle("$name $year") }
            }
    }

    /**
     * Names with more than the title ("Annie Hall Woody Allen", "Tron - Sci-Fi", "Leon aka The Professional") or a typo
     * ("Carribean") find nothing. Shorter queries do, but only an exact title or a very similar one released in the year
     * of the file counts, "Akira" (1988) is not "Akira Kurosawa" (1988).
     */
    private fun searchMovieByShortenedTitle(name: String, year: Int): MediaItem? {
        val normalizedName = normalizeMovieTitle(name)
        fun releasedNear(query: String) = (mediaProxyService.search(query, year, MediaType.Movie) + mediaProxyService.search(query, MediaType.Movie))
            .filter { it.type == MediaType.Movie && it.year?.let { releaseYear -> abs(releaseYear - year) <= 1 } == true }
        fun isSimilar(title: String) = normalizeMovieTitle(title) == normalizedName || isSimilarTitle(title, name)
        for (query in shortenedMovieQueries(name)) {
            val normalizedQuery = normalizeMovieTitle(query)
            val match = releasedNear(query).firstOrNull { item ->
                listOfNotNull(item.title, item.originalTitle).any { normalizeMovieTitle(it) == normalizedQuery || isSimilar(it) }
            }
            if (match != null) return match
        }
        // misspelled or differently written names ("Gold Finger", "Stephen Kings IT", "Pirates Of The Carribean") find
        // nothing as a whole, only the title itself counts
        for (query in spellingVariants(name)) {
            val match = releasedNear(query).firstOrNull { item -> listOfNotNull(item.title, item.originalTitle).any { isSimilar(it) } }
            if (match != null) return match
        }
        return null
    }

    /**
     * Possessives without the apostrophe ("Kings" to "King's"), words written apart ("Gold Finger") and the beginning of
     * a name, which a misspelling further back doesn't affect.
     */
    private fun spellingVariants(name: String): List<String> {
        val words = name.split(' ').filter { it.isNotBlank() }
        val variants = linkedSetOf<String>()
        val possessives = words.indices.filter { words[it].length >= 4 && words[it].endsWith('s', ignoreCase = true) }
        possessives.forEach { index -> variants.add(words.mapIndexed { i, word -> if (i == index) word.dropLast(1) + "'s" else word }.joinToString(" ")) }
        if (possessives.size > 1) variants.add(words.mapIndexed { i, word -> if (i in possessives) word.dropLast(1) + "'s" else word }.joinToString(" "))
        if (words.size in 2..6) {
            (0 until words.size - 1).forEach { index ->
                variants.add((words.take(index) + (words[index] + words[index + 1].lowercase()) + words.drop(index + 2)).joinToString(" "))
            }
        }
        (words.size - 1 downTo 1).forEach { count ->
            words.take(count).joinToString(" ").takeIf { query -> query.count { it.isLetter() } >= 4 }?.let { variants.add(it) }
        }
        variants.remove(name)
        return variants.toList()
    }

    /**
     * The parts of an "X aka Y" name, the part in front of a dash and the name without its last words, as long as two
     * words remain, e.g. "Annie Hall Woody Allen" to "Annie Hall Woody" and "Annie Hall".
     */
    private fun shortenedMovieQueries(name: String): List<String> {
        val queries = linkedSetOf<String>()
        queries.addAll(name.split(akaPattern).filter { it.isNotBlank() })
        name.substringBefore(" - ").takeIf { it != name }?.let { queries.add(it.trim()) }
        queries.addAll(name.split(akaPattern).first().substringBefore(" - ").trim().let { base ->
            val words = base.split(' ')
            // never inside a phrase like "To Live or Die on Everest"
            (words.size - 1 downTo maxOf(2, words.size - maxTrimmedWords))
                .filter { words[it].lowercase() !in titleConnectors && words[it - 1].lowercase() !in titleConnectors }
                .map { words.take(it).joinToString(" ") }
        })
        queries.remove(name)
        return queries.filter { it.isNotBlank() }
    }

    private fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        // swapped neighbours like "Legde" count as one edit
        val distance = Array(a.length + 1) { i -> IntArray(b.length + 1) { j -> if (i == 0) j else if (j == 0) i else 0 } }
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                distance[i][j] = minOf(distance[i - 1][j] + 1, distance[i][j - 1] + 1, distance[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) distance[i][j] = minOf(distance[i][j], distance[i - 2][j - 2] + 1)
            }
        }
        return 1.0 - distance[a.length][b.length].toDouble() / maxOf(a.length, b.length)
    }

    /**
     * Similar names of at least eight characters, but never another number or sequel: "The Hangover Part I" is not
     * "The Hangover Part II".
     */
    private fun isSimilarTitle(title: String, name: String): Boolean {
        // "Episode I" is "Episode 1"
        fun numbers(text: String) = MediaProxyService.normalizeTitle(text).split(' ').filter { it.matches(titleNumberPattern) }
            .map { romanNumerals[it] ?: it }
        val normalizedName = normalizeMovieTitle(name)
        return normalizedName.length >= 8 && numbers(title) == numbers(name) && similarity(normalizeMovieTitle(title), normalizedName) >= minTitleSimilarity
    }

    /**
     * Without a year only the title tells movies apart. The same title wins, otherwise a result that contains all words of
     * a name of at least two words ("Final Fantasy XV - Kingsglaive"). Namesakes without a release date come last,
     * "Smiley" is not "Rickey Smiley: Fool-ish" and "Elephant Man" is the one of 1980.
     */
    private fun searchMovieWithoutYear(name: String): MediaItem? {
        return searchMovieWithoutYearExact(name) ?: generateSequence(name) { it.replace(trailingMovieNoisePattern, "").trim() }
            .zipWithNext().takeWhile { (before, after) -> before != after && after.isNotBlank() }.take(2)
            .firstNotNullOfOrNull { (_, withoutNoise) -> searchMovieWithoutYearExact(withoutNoise, onlySameTitle = true) }
    }

    private fun searchMovieWithoutYearExact(name: String, onlySameTitle: Boolean = false): MediaItem? {
        val results = mediaProxyService.search(name, MediaType.Movie).filter { it.type == MediaType.Movie }.sortedBy { it.year == null }
        val normalizedName = normalizeMovieTitle(name)
        fun titles(item: MediaItem) = listOfNotNull(item.title, item.originalTitle)
        val words = MediaProxyService.normalizeTitle(name).split(' ').filter { it.isNotBlank() }.toSet()
        return results.firstOrNull { item -> titles(item).any { normalizeMovieTitle(it) == normalizedName } }
            ?: results.firstOrNull { item ->
                !onlySameTitle && words.size >= 2 && titles(item).any { title -> MediaProxyService.normalizeTitle(title).split(' ').toSet().containsAll(words) }
            }
            // "Grosse Point Blank" is "Grosse Pointe Blank"
            ?: results.firstOrNull { item ->
                titles(item).any { isSimilarTitle(it, name) }
            }
    }

    /**
     * Movie without a year, the first result only counts if it shares most words with the title, a series folder often
     * carries more than the movie's name.
     */
    fun searchMovieByTitle(name: String): MediaItem? {
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
}
