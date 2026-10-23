package boat.torrent

import boat.utilities.HttpHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.apache.commons.codec.binary.Hex
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

internal class TorrentInfoServiceScrapeTest {

    private val httpHelper: HttpHelper = mockk()

    private fun hash(index: Int) = String.format("%040x", index.toLong() * 0x0101010101L + 0xf0f0f0f0f0L)

    private fun torrents(count: Int) = (1..count).map { index ->
        Torrent("Test $index").apply { magnetUri = "magnet:?xt=urn:btih:" + hash(index) }
    }.toMutableList()

    /** hashes of the request, the url encoding is the binary hash */
    private fun requestedHashes(url: String): List<String> =
        url.substringAfter('?').split('&').map { Hex.encodeHexString(URLDecoder.decode(it.removePrefix("info_hash="), StandardCharsets.ISO_8859_1).toByteArray(StandardCharsets.ISO_8859_1)) }

    /** a scrape answer like a tracker sends it: only known hashes, sorted by their bytes */
    private fun answer(stats: Map<String, Pair<Int, Int>>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("d5:filesd".toByteArray())
        stats.toSortedMap().forEach { (hash, seedersAndLeechers) ->
            out.write("20:".toByteArray())
            out.write(Hex.decodeHex(hash))
            out.write("d8:completei${seedersAndLeechers.first}e10:downloadedi9e10:incompletei${seedersAndLeechers.second}ee".toByteArray())
        }
        out.write("ee".toByteArray())
        return out.toByteArray()
    }

    private fun service(): TorrentInfoService {
        every { httpHelper.getPage(match { it.endsWith("trackers_all_http.txt") }) } returns "http://tracker.example/announce\n"
        return TorrentInfoService(httpHelper)
    }

    @Test
    fun shouldMatchTheStatsByHashNotByOrder() {
        val service = service()
        val torrents = torrents(3)
        // the tracker answers sorted by hash and without the hash it doesn't know
        every { httpHelper.getBinary(any(), any()) } answers {
            HttpHelper.BinaryResponse(200, answer(mapOf(hash(3) to (30 to 3), hash(1) to (10 to 1))))
        }

        service.refreshTorrentStats(torrents)

        assertThat(torrents[0].seeder).isEqualTo(10)
        assertThat(torrents[0].leecher).isEqualTo(1)
        assertThat(torrents[0].statsVerified).isTrue
        assertThat(torrents[2].seeder).isEqualTo(30)
        assertThat(torrents[1].seeder).isEqualTo(-1)
        assertThat(torrents[1].statsVerified).isFalse
        service.shutdown()
    }

    @Test
    fun shouldKeepHashesWithBytesAbove127IntactInTheAnswer() {
        val service = service()
        val highBytes = "ff".repeat(10) + "80".repeat(10)
        val torrent = Torrent("High").apply { magnetUri = "magnet:?xt=urn:btih:$highBytes" }
        every { httpHelper.getBinary(any(), any()) } answers {
            assertThat(requestedHashes(firstArg())).containsExactly(highBytes)
            HttpHelper.BinaryResponse(200, answer(mapOf(highBytes to (7 to 2))))
        }

        service.refreshTorrentStats(mutableListOf(torrent))

        assertThat(torrent.seeder).isEqualTo(7)
        service.shutdown()
    }

    @Test
    fun shouldAskForManyTorrentsInFewRequests() {
        val service = service()
        val torrents = torrents(100)
        val requests = ConcurrentHashMap.newKeySet<Int>()
        every { httpHelper.getBinary(any(), any()) } answers {
            val hashes = requestedHashes(firstArg())
            requests.add(hashes.size)
            HttpHelper.BinaryResponse(200, answer(hashes.associateWith { 5 to 1 }))
        }

        service.refreshTorrentStats(torrents)

        verify(exactly = 3) { httpHelper.getBinary(any(), any()) }
        assertThat(requests).containsExactlyInAnyOrder(TorrentInfoService.BATCH_SIZE, 20)
        assertThat(torrents).allMatch { it.seeder == 5 && it.leecher == 1 && it.statsVerified }
        service.shutdown()
    }

    @Test
    fun shouldAskInSmallerBatchesIfTheTrackerRefusesTheRequest() {
        val service = service()
        val torrents = torrents(40)
        val maxAccepted = 10
        every { httpHelper.getBinary(any(), any()) } answers {
            val hashes = requestedHashes(firstArg())
            if (hashes.size > maxAccepted) HttpHelper.BinaryResponse(414, "<html>Request-URI Too Long</html>".toByteArray())
            else HttpHelper.BinaryResponse(200, answer(hashes.associateWith { 4 to 0 }))
        }

        service.refreshTorrentStats(torrents)

        assertThat(torrents).allMatch { it.seeder == 4 && it.statsVerified }
        // 40 -> 20 + 20 -> 4 x 10: 1 + 2 + 4 requests
        verify(exactly = 7) { httpHelper.getBinary(any(), any()) }
        service.shutdown()
    }

    @Test
    fun shouldNotSplitForAnyOtherErrorOrAnswer() {
        listOf(
            HttpHelper.BinaryResponse(404, "<html>not found</html>".toByteArray()),
            HttpHelper.BinaryResponse(500, "<html>error</html>".toByteArray()),
            HttpHelper.BinaryResponse(200, "not bencode".toByteArray()),
        ).forEach { response ->
            io.mockk.clearMocks(httpHelper)
            val service = service()
            val torrents = torrents(40)
            every { httpHelper.getBinary(any(), any()) } returns response

            service.refreshTorrentStats(torrents)

            assertThat(torrents).noneMatch { it.statsVerified }
            verify(exactly = 1) { httpHelper.getBinary(any(), any()) }
            service.shutdown()
        }
    }

    @Test
    fun shouldNotRetryIfTheTrackerCanNotBeReached() {
        val service = service()
        val torrents = torrents(40)
        every { httpHelper.getBinary(any(), any()) } throws IOException("connection refused")

        service.refreshTorrentStats(torrents)

        verify(exactly = 1) { httpHelper.getBinary(any(), any()) }
        assertThat(torrents).allMatch { it.seeder == -1 }
        service.shutdown()
    }

    @Test
    fun shouldNotSplitIfTheTrackerAnswersWithAFailureReason() {
        val service = service()
        every { httpHelper.getBinary(any(), any()) } returns HttpHelper.BinaryResponse(200, "d14:failure reason7:blockede".toByteArray())

        service.refreshTorrentStats(torrents(40))

        verify(exactly = 1) { httpHelper.getBinary(any(), any()) }
        service.shutdown()
    }

    @Test
    fun shouldSkipCachedTorrentsInvalidHashesAndAskOnceForDuplicates() {
        val service = service()
        val cached = Torrent("Cached").apply { magnetUri = "magnet:?xt=urn:btih:" + hash(1); cached.add("Premiumize") }
        val first = Torrent("Dune").apply { magnetUri = "magnet:?xt=urn:btih:" + hash(2) }
        val duplicate = Torrent("Dune again").apply { magnetUri = "magnet:?xt=urn:btih:" + hash(2) }
        val noHash = Torrent("No hash").apply { magnetUri = "https://example.org/file.torrent" }
        every { httpHelper.getBinary(any(), any()) } answers {
            val hashes = requestedHashes(firstArg())
            assertThat(hashes).containsExactly(hash(2))
            HttpHelper.BinaryResponse(200, answer(mapOf(hash(2) to (8 to 1))))
        }

        service.refreshTorrentStats(mutableListOf(cached, first, duplicate, noHash))

        verify(exactly = 1) { httpHelper.getBinary(any(), any()) }
        assertThat(first.seeder).isEqualTo(8)
        assertThat(duplicate.seeder).isEqualTo(8)
        assertThat(cached.seeder).isEqualTo(-1)
        assertThat(noHash.seeder).isEqualTo(-1)
        service.shutdown()
    }

    @Test
    fun shouldUseABoundedNumberOfThreadsForManyBatches() {
        val service = service()
        val running = AtomicInteger()
        val maxRunning = AtomicInteger()
        val threads = ConcurrentHashMap.newKeySet<Thread>()
        every { httpHelper.getBinary(any(), any()) } answers {
            threads.add(Thread.currentThread())
            maxRunning.accumulateAndGet(running.incrementAndGet(), ::maxOf)
            Thread.sleep(30)
            running.decrementAndGet()
            val hashes = requestedHashes(firstArg())
            HttpHelper.BinaryResponse(200, answer(hashes.associateWith { 1 to 1 }))
        }

        val torrents = torrents(1000)
        repeat(2) { service.refreshTorrentStats(torrents) }

        assertThat(torrents).allMatch { it.seeder == 1 }
        assertThat(maxRunning.get()).isBetween(2, TorrentInfoService.MAX_PARALLEL_TRACKER_REQUESTS)
        assertThat(threads.size).isLessThanOrEqualTo(TorrentInfoService.MAX_PARALLEL_TRACKER_REQUESTS)
        service.shutdown()
    }
}
