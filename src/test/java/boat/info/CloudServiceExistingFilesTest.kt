package boat.info

import boat.torrent.TorrentType
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class CloudServiceExistingFilesTest {

    private val cloudFileService: CloudFileService = mockk(relaxed = true)
    private lateinit var cloudService: CloudService

    private val existingMovies = listOf(
        "Italian.Job.2003.1080p.mkv",
        "Hitman.2007.720p.mkv",
        "It.2017.1080p.mkv",
        "Inception {tmdb-27205}.mkv",
    )

    @BeforeEach
    fun setUp() {
        every { cloudFileService.getFilesInPath(any()) } returns emptyList()
        cloudService = CloudService(cloudFileService)
        val moviesPath = cloudService.buildDestinationPathWithTypeOfMediaWithoutSubFolders("I", TorrentType.MOVIES)
        every { cloudFileService.getFilesInPath(moviesPath) } returns existingMovies.map { moviesPath + it }
        every { cloudFileService.isCacheFilled } returns true
    }

    @Test
    fun shouldFindNoFilesForEmptySearch() {
        assertThat(cloudService.findExistingFiles("")).isEmpty()
        assertThat(cloudService.findExistingFiles("   ")).isEmpty()
    }

    @Test
    fun shouldMatchWholeWordsOnly() {
        assertThat(cloudService.findExistingFiles("It")).hasSize(1)
        assertThat(cloudService.findExistingFiles("It 2017")).hasSize(1)
        assertThat(cloudService.findExistingFiles("Job")).hasSize(1)
        assertThat(cloudService.findExistingFiles("Itali")).isEmpty()
    }

    @Test
    fun shouldMatchCaseInsensitive() {
        assertThat(cloudService.findExistingFiles("italian JOB")).hasSize(1)
    }

    @Test
    fun shouldMatchRemoteMediaId() {
        assertThat(cloudService.findExistingFiles("{tmdb-27205}")).hasSize(1)
        assertThat(cloudService.findExistingFiles("{tmdb-2720}")).isEmpty()
    }

    @Test
    fun shouldFindNoFilesIfCacheIsNotFilled() {
        every { cloudFileService.isCacheFilled } returns false
        assertThat(cloudService.findExistingFiles("It")).isEmpty()
    }
}
