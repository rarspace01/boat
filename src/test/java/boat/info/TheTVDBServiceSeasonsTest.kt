package boat.info

import boat.utilities.HttpHelper
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class TheTVDBServiceSeasonsTest {

    private val service = TheTVDBService(mockk<HttpHelper>())

    private fun series(seasons: String) =
        """{"data":{"id":334824,"name":"Dobi","year":"2017","seasons":$seasons}}"""

    @Test
    fun shouldCountTheOfficialSeasonsWithoutSpecialsAndOtherOrders() {
        val page = series(
            """[{"number":0,"type":{"type":"official"}},{"number":1,"type":{"type":"official"}},{"number":2,"type":{"type":"official"}},
            {"number":3,"type":{"type":"official"}},{"number":0,"type":{"type":"dvd"}},{"number":5,"type":{"type":"dvd"}},{"number":1,"type":{"type":"absolute"}}]"""
        )

        assertThat(service.parseSeriesPage(page)?.seasonCount).isEqualTo(3)
    }

    @Test
    fun shouldCountMoreThanTenSeasonsInAnyOrder() {
        val official = (17 downTo 0).joinToString(",") { """{"number":$it,"type":{"id":1,"name":"Aired Order","type":"official","alternateName":null}}""" }
        val page = series("""[$official,{"number":20,"type":{"id":2,"name":"DVD Order","type":"dvd","alternateName":null}}]""")

        assertThat(service.parseSeriesPage(page)?.seasonCount).isEqualTo(17)
    }

    @Test
    fun shouldNotMakeUpASeasonCount() {
        assertThat(service.parseSeriesPage(series("[]"))?.seasonCount).isNull()
        assertThat(service.parseSeriesPage("""{"data":{"id":1,"name":"No seasons field","year":"2000"}}""")?.seasonCount).isNull()
        assertThat(service.parseSeriesPage(series("""[{"number":0,"type":{"type":"official"}}]"""))?.seasonCount).isNull()
    }
}
