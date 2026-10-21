package boat.torrent

import boat.utilities.HttpHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

internal class TorrentInfoServiceExecutorTest {

    private val httpHelper: HttpHelper = mockk()

    private fun torrents(count: Int) = (0 until count).map { index ->
        Torrent("Test $index").apply { magnetUri = "magnet:?xt=urn:btih:" + String.format("%040x", index + 1) }
    }.toMutableList()

    private fun service(): TorrentInfoService {
        every { httpHelper.getPage(match { it.endsWith("trackers_all_http.txt") }) } returns "http://tracker.example/announce\n"
        return TorrentInfoService(httpHelper)
    }

    @Test
    fun shouldAskTheTrackerForEveryTorrentWithABoundedNumberOfThreads() {
        val service = service()
        val running = AtomicInteger()
        val maxRunning = AtomicInteger()
        every { httpHelper.getPage(match { it.contains("scrape") }, any<Int>()) } answers {
            maxRunning.accumulateAndGet(running.incrementAndGet(), ::maxOf)
            Thread.sleep(30)
            running.decrementAndGet()
            "d5:filesd20:xd8:completei5e10:downloadedi1e10:incompletei2eeee"
        }

        val torrents = torrents(40)
        service.refreshTorrentStats(torrents)

        verify(exactly = 40) { httpHelper.getPage(match { it.contains("scrape") }, any<Int>()) }
        assertThat(maxRunning.get()).isBetween(2, TorrentInfoService.MAX_PARALLEL_TRACKER_REQUESTS)
        assertThat(torrents).allMatch { it.seeder == 5 && it.leecher == 2 }
        service.shutdown()
    }

    @Test
    fun shouldSurviveATorrentWhoseRequestFails() {
        val service = service()
        every { httpHelper.getPage(match { it.contains("scrape") }, any<Int>()) } throws RuntimeException("tracker down")

        service.refreshTorrentStats(torrents(3))

        service.shutdown()
    }

    @Test
    fun shouldReuseTheSameThreadsForEverySearch() {
        val service = service()
        val threads = java.util.concurrent.ConcurrentHashMap.newKeySet<Thread>()
        every { httpHelper.getPage(match { it.contains("scrape") }, any<Int>()) } answers {
            threads.add(Thread.currentThread())
            ""
        }

        repeat(5) { service.refreshTorrentStats(torrents(20)) }

        assertThat(threads.size).isLessThanOrEqualTo(TorrentInfoService.MAX_PARALLEL_TRACKER_REQUESTS)
        service.shutdown()
    }
}
