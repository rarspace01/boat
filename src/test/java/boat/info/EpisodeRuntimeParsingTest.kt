package boat.info

import boat.utilities.HttpHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class EpisodeRuntimeParsingTest {
    private val tmdb = TheMovieDataBaseService(HttpHelper())
    private val tvdb = TheTVDBService(HttpHelper())

    @Test
    fun `should read the runtime of a tmdb movie`() {
        assertThat(tmdb.parseRuntime("""{"id": 27205, "runtime": 148}""")).isEqualTo(148)
        assertThat(tmdb.parseRuntime("""{"id": 1, "runtime": 0}""")).isNull()
        assertThat(tmdb.parseRuntime("""{"id": 1, "runtime": null}""")).isNull()
        assertThat(tmdb.parseRuntime("no json")).isNull()
    }

    @Test
    fun `should read the runtimes of the episodes of a tmdb season`() {
        val page = """{"episodes": [{"episode_number": 1, "runtime": 58}, {"episode_number": 2, "runtime": null}, {"episode_number": 3, "runtime": 47}]}"""
        assertThat(tmdb.parseEpisodeRuntimes(page)).isEqualTo(mapOf(1 to 58, 3 to 47))
        assertThat(tmdb.parseEpisodeRuntimes("""{"success": false}""")).isEmpty()
    }

    @Test
    fun `should read the runtimes of the episodes of a tvdb season`() {
        val page = """{"data": {"episodes": [
            {"seasonNumber": 2, "number": 1, "runtime": 43}, {"seasonNumber": 2, "number": 2, "runtime": null},
            {"seasonNumber": 3, "number": 1, "runtime": 40}]}}"""
        assertThat(tvdb.parseEpisodeRuntimes(page, 2)).isEqualTo(mapOf(1 to 43))
        assertThat(tvdb.parseEpisodeRuntimes("{}", 2)).isEmpty()
    }
}
