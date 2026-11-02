package boat.info

import boat.utilities.HttpHelper
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class TheMovieDataBaseServiceResponseTest {

    private val service = TheMovieDataBaseService(mockk<HttpHelper>())

    @Test
    fun shouldReturnNullIfThereWasNoUsableAnswer() {
        assertThat(service.parseResponsePage(null)).isNull()
        assertThat(service.parseResponsePage("")).isNull()
        assertThat(service.parseResponsePage("<html>502 Bad Gateway</html>")).isNull()
        assertThat(service.parseResponsePage("[]")).isNull()
        assertThat(service.parseResponsePage("""{"status_code":7,"status_message":"Invalid API key","success":false}""")).isNull()
    }

    @Test
    fun shouldReturnAnEmptyListForAnAnswerWithoutResults() {
        assertThat(service.parseResponsePage("""{"page":1,"results":[],"total_results":0}""")).isEmpty()
    }

    @Test
    fun shouldReadTheTvdbIdOfASeries() {
        assertThat(service.parseTvdbId("""{"id":34549,"imdb_id":"tt1","tvdb_id":184871,"tvrage_id":null}""")).isEqualTo("184871")
        assertThat(service.parseTvdbId("""{"id":34549,"tvdb_id":null}""")).isNull()
        assertThat(service.parseTvdbId("""{"status_code":34,"success":false}""")).isNull()
        assertThat(service.parseTvdbId("<html>502 Bad Gateway</html>")).isNull()
        assertThat(service.parseTvdbId(null)).isNull()
    }
}
