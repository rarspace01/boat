package boat

import boat.info.MediaItem
import boat.info.MediaProxyService
import boat.info.MediaType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class ContentRenamerMovieTest {

    private val mediaProxyService: MediaProxyService = mockk(relaxed = true)
    private val contentRenamer = ContentRenamer(mediaProxyService)

    private val inception = MediaItem("Inception", "Inception", 2010, MediaType.Movie, "{tmdb-27205}")

    private fun createFile(base: File, relativePath: String): File {
        return File(base, relativePath).apply {
            parentFile.mkdirs()
            writeText("x")
        }
    }

    @Test
    fun `should remove release groups attached with dash`() {
        assertThat(contentRenamer.removeReleaseGroups("sparks-inception")).isEqualTo("inception")
        assertThat(contentRenamer.removeReleaseGroups("Inception.2010.1080p.BluRay.x264-SPARKS")).isEqualTo("Inception.2010.1080p.BluRay.x264")
        assertThat(contentRenamer.removeReleaseGroups("Spider-Man")).isEqualTo("Spider-Man")
        assertThat(contentRenamer.removeReleaseGroups("RARBG")).isEqualTo("RARBG")
        assertThat(contentRenamer.extractName(File("~/Movies/I/Inception.2010.1080p.BluRay.x264-SPARKS/sparks-inception.mkv"))).isEqualToIgnoringCase("inception")
        // a file named like a release group falls back to the folder name
        assertThat(contentRenamer.extractName(File("~/Movies/I/Inception.2010.1080p.BluRay.x264-SPARKS/RARBG.txt"))).isEqualTo("Inception")
    }

    @Test
    fun `should strip remote media id from extracted name`() {
        assertThat(contentRenamer.extractName(File("~/Movies/I/Inception (2010) {tmdb-27205}.1080p.x264.mkv"))).isEqualTo("Inception")
    }

    @Test
    fun `should search movies with numbers in the title as movie in movies folder`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        createFile(base, "Movies/A/Apollo 13 (1995)/Apollo 13 (1995).mkv")
        val apollo = MediaItem("Apollo 13", "Apollo 13", 1995, MediaType.Movie, "{tmdb-568}")
        every { mediaProxyService.search("Apollo 13", 1995, MediaType.Movie) } returns listOf(apollo)

        contentRenamer.processFilesFromDirectory(File(base, "Movies").absolutePath, null, isTestRun = false, basePath = base.absolutePath)

        verify { mediaProxyService.search("Apollo 13", 1995, MediaType.Movie) }
        assertThat(File(base, "Movies/A/Apollo 13 (1995) {tmdb-568}.mkv")).exists()
    }

    @Test
    fun `should use remote media id of already renamed files`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        val renamed = createFile(base, "Movies/I/Inception (2010) {tmdb-27205}.1080p.x264.mkv")
        every { mediaProxyService.findByRemoteMediaId("{tmdb-27205}", any(), MediaType.Movie) } returns inception

        contentRenamer.processFilesFromDirectory(File(base, "Movies").absolutePath, null, isTestRun = false, basePath = base.absolutePath)

        verify(exactly = 0) { mediaProxyService.search(any(), any<Int>(), any()) }
        verify(exactly = 0) { mediaProxyService.search(any(), any<MediaType>()) }
        assertThat(renamed).exists()
    }

    @Test
    fun `should only rename video and subtitle files of movies`(@TempDir tempDir: Path) {
        val base = tempDir.toFile()
        val folder = "Movies/I/Inception.2010.1080p.BluRay.x264-SPARKS"
        createFile(base, "$folder/sparks-inception.mkv")
        createFile(base, "$folder/Inception.2010.en.srt")
        val nfo = createFile(base, "$folder/RARBG.txt")
        every { mediaProxyService.search(any(), 2010, MediaType.Movie) } returns listOf(inception)

        contentRenamer.processFilesFromDirectory(File(base, "Movies").absolutePath, null, isTestRun = false, basePath = base.absolutePath)

        assertThat(File(base, "Movies/I/Inception (2010) {tmdb-27205}.1080p.x264.mkv")).exists()
        assertThat(File(base, "Movies/I/Inception (2010) {tmdb-27205}.1080p.x264.en.srt")).exists()
        assertThat(nfo).exists()
    }
}
