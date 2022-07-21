package boat.multifileHoster

import boat.info.CloudService
import boat.model.Transfer
import boat.services.TransferService
import boat.torrent.Torrent
import boat.torrent.TorrentFile
import boat.utilities.HttpHelper
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.util.List

internal class MultifileHosterServiceTest {

    private val httpHelper: HttpHelper = mockk(relaxed = true)
    private val transferService: TransferService = mockk(relaxed = true)
    private val cloudService: CloudService = mockk(relaxed = true)
    lateinit var multifileHosterService: MultifileHosterService

    @BeforeEach
    fun beforeEach() {
        multifileHosterService = MultifileHosterService(httpHelper, transferService, cloudService)
    }

    @Test
    fun extractRemoteIdFromMessage() {
        // Given
        val transferFeedbackMessage =
            "{\"status\":\"success\",\"id\":\"0jXfg462WZz2EkSpFKjU_g\",\"name\":\"magnet:?xt=urn:btih:123456789\",\"type\":\"torrent\"}"
        val transfer = Transfer(feedbackMessage = transferFeedbackMessage)
        // When
        val remoteIdFromMessage =
            multifileHosterService.extractRemoteIdFromMessage(transfer)
        // Then
        assertThat(remoteIdFromMessage).isEqualTo("0jXfg462WZz2EkSpFKjU_g")
    }

    @Test
    fun extractOtherRemoteIdFromMessage() {
        // Given
        val transferFeedbackMessage =
            "{\"status\":\"success\",\"id\":\"yxGo09Kq-RkayKbAxdDyFQ\",\"name\":\"magnet:?xt=urn:btih:12345678912316782351328765\",\"type\":\"torrent\"}"
        val transfer = Transfer(feedbackMessage = transferFeedbackMessage)
        // When
        val remoteIdFromMessage =
            multifileHosterService.extractRemoteIdFromMessage(transfer)
        // Then
        assertThat(remoteIdFromMessage).isEqualTo("yxGo09Kq-RkayKbAxdDyFQ")
    }

    @Test
    fun transferMatchedTorrentByName() {
        // Given
        val transfer = Transfer()
        transfer.name = "the adventures of buckaroo banzai across the 8th dimension (1984) (1080p bluray .."
        val torrent = Torrent("Test")
        torrent.name = "The Adventures of Buckaroo Banzai Across the 8th Dimension (1984) (1080p BluRay x265 HEVC 10bit AAC 5.1 Tigole)"
        torrent.remoteId = "remoteId"
        // When

        val matched =
            multifileHosterService.transferMatchedTorrentByName(transfer, torrent, 2)
        // Then
        assertThat(matched).isTrue
    }

    @Test
    fun shouldGetETABeforeFirstFile() {
        // Given
        val torrent = Torrent("Test")
        torrent.sizeInMB = 1000.0
        val torrentFile = TorrentFile(
            id = "", name = "",
            filesize = (1024 * 1024 * 500).toLong(),
            url = ""
        )
        // When
        val uploadStatusString = multifileHosterService.getUploadStatusString(torrent, List.of(torrentFile), 0, null)
        // Then
        assertThat(uploadStatusString).doesNotMatch("Uploading: 0/1 done ETA: 00:00:00")
    }

    @Test
    fun shouldBuildProperFilenames() {
        // When
        val filename = multifileHosterService
            .buildFilename("www.url.lol - Movie Title 2018", "www.movie-url.lol-movie_title_2018.mkv")
        // Then
        assertThat(filename).isEqualTo("Movie.Title.2018.mkv")
    }

    @Test
    fun shouldBuildProperFilenamesWithoutQuotesAndTorrentInName() {
        // When
        val filename = multifileHosterService
            .buildFilename("\"www.url.lol - Movie Title 2018.torrent\"", "www.movie-url.lol-movie_title_2018.mkv")
        // Then
        assertThat(filename).isEqualTo("Movie.Title.2018.mkv")
    }

    @Test
    fun shouldBuildProperFilenameFromFileIfEmptyName() {
        // When
        val filename = multifileHosterService
            .buildFilename("", "www.movie-url.lol-movie.title.2018.mkv")
        // Then
        assertThat(filename).isEqualTo("movie.title.2018.mkv")
    }

    @Test
    fun shouldBuildProperFilenameFromFileIfNullName() {
        // When
        val filename = multifileHosterService
            .buildFilename(null, "www.movie-url.lol-movie.title.2018.mkv")
        // Then
        assertThat(filename).isEqualTo("movie.title.2018.mkv")
    }

    @Disabled
    @Test
    fun getTorrentToBeDownloaded() {
        // Given
        // When
        val torrentToBeDownloaded = multifileHosterService.getTorrentToBeDownloaded()
        // Then
        Assertions.assertNotNull(torrentToBeDownloaded)
    }
}
