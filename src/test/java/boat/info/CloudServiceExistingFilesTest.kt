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
        "Im Still Here {tmdb-1}.mkv",
        "Ive Seen Memento {tmdb-2}.mkv",
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
    fun shouldFindTitlesWithApostropheWithoutOneInTheLibrary() {
        assertThat(cloudService.findExistingFiles("I'm Still Here")).hasSize(1)
        assertThat(cloudService.findExistingFiles("Im Still Here")).hasSize(1)
        assertThat(cloudService.findExistingFiles("I've Seen Memento")).hasSize(1)
    }

    @Test
    fun shouldFindFilesWithApostropheForASearchWithout() {
        val moviesPath = cloudService.buildDestinationPathWithTypeOfMediaWithoutSubFolders("D", TorrentType.MOVIES)
        every { cloudFileService.getFilesInPath(moviesPath) } returns listOf(moviesPath + "Don't Look Up {tmdb-3}.mkv")

        val found = cloudService.findExistingFiles("Dont Look Up")

        assertThat(found).hasSize(1)
        assertThat(found.single()).contains("Don't Look Up")
    }

    @Test
    fun shouldMatchRemoteMediaId() {
        assertThat(cloudService.findExistingFiles("{tmdb-27205}")).hasSize(1)
        assertThat(cloudService.findExistingFiles("{tmdb-2720}")).isEmpty()
    }

    @Test
    fun shouldListTheFilesIfCacheIsNotFilledYet() {
        every { cloudFileService.isCacheFilled } returns false
        assertThat(cloudService.findExistingFiles("It")).hasSize(1)
    }
}
