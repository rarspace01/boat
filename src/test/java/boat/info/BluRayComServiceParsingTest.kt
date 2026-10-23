package boat.info

import boat.utilities.HttpHelper
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.IOException

internal class BluRayComServiceParsingTest {

    private val httpHelper: HttpHelper = mockk()
    private val service = BluRayComService(httpHelper)

    // lines as they are on https://www.blu-ray.com/movies/releasedates.php?year=2021&month=1
    private val page = """
        <script language="JavaScript">
        var movies = new Array();
        movies[0] = {id: 295087, casing: '', artworkurl: '', title_sort: 'Steven Sonivi 5-Film Collection 2005', title: 'Steven Sonivi 5-Film Collection', edition: '', extended: '', title_keywords: 'Steven-Sonivi-5-Film-Collection', studio: 'Echo Bridge Entertainment', year: '2005', yearend: '2018', releasedate: 'January 01, 2021', popularity: 99, width: 156, height: 198};
        movies[22] = {id: 280476, casing: '', artworkurl: '', title_sort: 'JmAc\'s Bizarre Adventure 2016', title: 'JmAc\'s Bizarre Adventure: Set 4 - Diamond is Unbreakable Part 1', edition: '', extended: '', title_keywords: 'JoJos', studio: 'Viz', year: '2016', yearend: '2016', releasedate: 'January 05, 2021', popularity: 1, width: 156, height: 198};
        movies[26] = {id: 280226, casing: '', artworkurl: '', title_sort: '100 007 The 100  The Seventh and Final Season 2020', title: 'The 100: The Seventh and Final Season', edition: '', extended: 'Warner Archive Collection', title_keywords: 'The-100', studio: 'Warner Bros.', year: '2020', yearend: '2020', releasedate: 'January 05, 2021', popularity: 1, width: 156, height: 198};
        movies[27] = {id: 1, casing: '', artworkurl: '', title_sort: 'Unknown', title: 'Without a year', year: '0000', releasedate: 'January 05, 2021'};
        movies[28] = {id: 2, casing: '', artworkurl: '', title_sort: 'No title', year: '2000'};
        var other = {title: 'not a movie line', year: '1999'};
        </script>
    """.trimIndent()

    @Test
    fun shouldParseTheReleasesOfThePage() {
        val releases = service.parseReleases(page)

        assertThat(releases).containsExactly(
            MediaItem("Steven Sonivi 5-Film Collection", "Steven Sonivi 5-Film Collection", 2005, MediaType.Movie),
            MediaItem("JmAc's Bizarre Adventure: Set 4 - Diamond is Unbreakable Part 1", "JmAc's Bizarre Adventure: Set 4 - Diamond is Unbreakable Part 1", 2016, MediaType.Movie),
            MediaItem("The 100: The Seventh and Final Season", "The 100: The Seventh and Final Season", 2020, MediaType.Series),
            MediaItem("Without a year", "Without a year", null, MediaType.Movie),
        )
    }

    @Test
    fun shouldReadThePageAsIso88591() {
        val body = "movies[0] = {id: 1, title: 'Acúzuf', year: '2001'};".toByteArray(Charsets.ISO_8859_1)
        every { httpHelper.getBinary(any(), any(), any()) } returns HttpHelper.BinaryResponse(200, body)

        assertThat(service.getReleasesForMonthAndYear(1, 2021).map { it.title }).containsExactly("Acúzuf")
    }

    @Test
    fun shouldIdentifyAsBoatInsteadOfPretendingToBeABrowser() {
        every { httpHelper.getBinary(any(), any(), any()) } returns HttpHelper.BinaryResponse(200, ByteArray(0))

        service.getReleasesForMonthAndYear(1, 2021)

        io.mockk.verify { httpHelper.getBinary(match { it.contains("year=2021&month=1") }, any(), match { it["User-Agent"]?.startsWith("boat") == true }) }
    }

    @Test
    fun shouldReturnNothingIfThePageCanNotBeLoaded() {
        every { httpHelper.getBinary(any(), any(), any()) } returns HttpHelper.BinaryResponse(503, ByteArray(0))
        assertThat(service.getReleasesForMonthAndYear(1, 2021)).isEmpty()

        every { httpHelper.getBinary(any(), any(), any()) } throws IOException("down")
        assertThat(service.getReleasesForMonthAndYear(1, 2021)).isEmpty()
    }
}
