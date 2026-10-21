package boat.multifileHoster

import boat.model.TransferStatus
import boat.torrent.Torrent
import boat.utilities.HttpHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration

internal class PremiumizeParsingTest {

    private val httpHelper: HttpHelper = mockk(relaxed = true)
    private val premiumize = Premiumize(httpHelper)

    private fun torrents(count: Int): List<Torrent> = (0 until count).map { index ->
        Torrent("Test").apply { magnetUri = "magnet:?xt=urn:btih:" + String.format("%040x", index) + "&dn=Torrent$index" }
    }

    private fun answerCacheCheckWithAllCached() {
        every { httpHelper.getPage(match { it.contains("cache/check") }) } answers {
            val numberOfItems = firstArg<String>().split("&items%5B%5D=").size - 1
            "{\"status\":\"success\",\"response\":[" + List(numberOfItems) { "true" }.joinToString(",") + "]}"
        }
    }

    @Test
    fun shouldCheckCacheStateOfAllTorrentsIfMoreThanOneBatch() {
        // Given
        val torrents = torrents(250)
        answerCacheCheckWithAllCached()
        // When
        premiumize.enrichCacheStateOfTorrents(torrents)
        // Then
        assertThat(torrents).allMatch { it.cached.contains("Premiumize") }
        verify(exactly = 3) { httpHelper.getPage(match { it.contains("cache/check") }) }
    }

    @Test
    fun shouldCheckCacheStateOfLastTorrentIfOneMoreThanBatchSize() {
        // Given
        val torrents = torrents(101)
        answerCacheCheckWithAllCached()
        // When
        premiumize.enrichCacheStateOfTorrents(torrents)
        // Then
        assertThat(torrents.last().cached).containsExactly("Premiumize")
    }

    @Test
    fun shouldCheckCacheStateOfSingleBatch() {
        // Given
        val torrents = torrents(100)
        answerCacheCheckWithAllCached()
        // When
        premiumize.enrichCacheStateOfTorrents(torrents)
        // Then
        assertThat(torrents).allMatch { it.cached.contains("Premiumize") }
        verify(exactly = 1) { httpHelper.getPage(match { it.contains("cache/check") }) }
    }

    private fun transferJson(id: String, message: String) =
        "{\"id\":\"$id\",\"name\":\"Name $id\",\"message\":\"$message\",\"status\":\"running\",\"progress\":0.5," +
                "\"src\":\"magnet:?xt=urn:btih:abc$id\",\"folder_id\":\"folder$id\",\"file_id\":\"file$id\"}"

    private fun givenTransferList(vararg transfers: String) {
        every { httpHelper.getPage(match { it.contains("transfer/list") }) } returns
                "{\"status\":\"success\",\"transfers\":[" + transfers.joinToString(",") + "]}"
    }

    @Test
    fun shouldParseRemoteTorrents() {
        // Given
        givenTransferList(transferJson("1", "Downloading, 50% done, 2 days left"))
        // When
        val remoteTorrents = premiumize.getRemoteTorrents()
        // Then
        assertThat(remoteTorrents).hasSize(1)
        val torrent = remoteTorrents.first()
        assertThat(torrent.remoteId).isEqualTo("1")
        assertThat(torrent.name).isEqualTo("Name 1")
        assertThat(torrent.folder_id).isEqualTo("folder1")
        assertThat(torrent.magnetUri).isEqualTo("magnet:?xt=urn:btih:abc1")
        assertThat(torrent.remoteTransferStatus).isEqualTo(TransferStatus.DOWNLOADING_TO_MULTIHOSTER)
        assertThat(torrent.remoteProgressInPercent).isEqualTo(0.5)
        assertThat(torrent.eta).isEqualTo(Duration.ofDays(2))
    }

    @Test
    fun shouldParseSingularAndApproximateEta() {
        // Given
        givenTransferList(
            transferJson("1", "Downloading, 50% done, 1 hour left"),
            transferJson("2", "Downloading, 50% done, about 3 mins left"),
            transferJson("3", "Downloading, 50% done, 1 second left"),
            transferJson("4", "Downloading, 50% done, unknown time left"),
        )
        // When
        val etas = premiumize.getRemoteTorrents().map { it.eta }
        // Then
        assertThat(etas).containsExactly(Duration.ofHours(1), Duration.ofMinutes(3), Duration.ofSeconds(1), Duration.ZERO)
    }

    @Test
    fun shouldSkipMalformedTransferButKeepTheOthers() {
        // Given
        val transferWithoutMessage = "{\"id\":\"2\",\"name\":\"Broken\",\"status\":\"running\",\"progress\":0.1}"
        givenTransferList(transferJson("1", "Downloading, 50% done, 1 hour left"), transferWithoutMessage, transferJson("3", ""))
        // When
        val remoteTorrents = premiumize.getRemoteTorrents()
        // Then
        assertThat(remoteTorrents.map { it.remoteId }).containsExactly("1", "3")
    }

    @Test
    fun shouldReturnNoRemoteTorrentsForInvalidResponse() {
        // Given
        every { httpHelper.getPage(match { it.contains("transfer/list") }) } returns "<html>502 Bad Gateway</html>"
        // When
        val remoteTorrents = premiumize.getRemoteTorrents()
        // Then
        assertThat(remoteTorrents).isEmpty()
    }

    @Test
    fun shouldListFilesOfFolderAndSubfolder() {
        every { httpHelper.getPage(match { it.contains("id=root") }) } returns
                """{"content":[{"type":"folder","id":"sub","name":"Sub"},{"type":"file","id":"1","name":"a.mkv","size":10,"link":"http://a"}]}"""
        every { httpHelper.getPage(match { it.contains("id=sub") }) } returns
                """{"content":[{"type":"file","id":"2","name":"b.mkv","size":20,"link":"http://b"}]}"""

        val files = premiumize.getFilesFromTorrent(Torrent("Test").apply { folder_id = "root" })

        assertThat(files.map { it.name }).containsExactlyInAnyOrder("a.mkv", "Sub/b.mkv")
    }

    @Test
    fun shouldReturnNoFilesInsteadOfAPartialListIfASubfolderFails() {
        every { httpHelper.getPage(match { it.contains("id=root") }) } returns
                """{"content":[{"type":"file","id":"1","name":"a.mkv","size":10,"link":"http://a"},{"type":"folder","id":"sub","name":"Sub"}]}"""
        every { httpHelper.getPage(match { it.contains("id=sub") }) } returns "not json"

        val files = premiumize.getFilesFromTorrent(Torrent("Test").apply { folder_id = "root" })

        assertThat(files).isEmpty()
    }

    @Test
    fun shouldReturnNoFilesIfAFileHasNoLink() {
        every { httpHelper.getPage(match { it.contains("id=root") }) } returns
                """{"content":[{"type":"file","id":"1","name":"a.mkv","size":10}]}"""

        val files = premiumize.getFilesFromTorrent(Torrent("Test").apply { folder_id = "root" })

        assertThat(files).isEmpty()
    }
}
