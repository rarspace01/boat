package boat.torrent.searchEngines

import boat.utilities.LoggerDelegate

/** Parsing the hits of a result page. */
internal object SearchHits {
    private val logger by LoggerDelegate()

    /** Every hit on its own: a malformed one is skipped and logged (the hit, not the whole page), the others are kept. */
    fun <T, R : Any> parseEach(engine: Any, hits: Iterable<T>, parse: (T) -> R?): List<R> = hits.mapNotNull { hit ->
        try {
            parse(hit)
        } catch (exception: Exception) {
            logger.warn("{}: skipped a hit which could not be read ({}): {}", engine, exception.toString(), hit.toString().take(MAX_LOGGED_CHARS))
            null
        }
    }

    /** A page which could not be read at all, only its beginning is logged. */
    fun logUnreadablePage(engine: Any, pageContent: String, exception: Exception) {
        logger.error("{}: could not read the result page ({}): {}", engine, exception.toString(), pageContent.take(MAX_LOGGED_CHARS))
    }

    private const val MAX_LOGGED_CHARS = 500
}
