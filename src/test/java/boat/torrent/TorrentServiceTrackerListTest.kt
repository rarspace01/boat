package boat.torrent

import boat.utilities.HttpHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class TorrentServiceTrackerListTest {

    private val httpHelper: HttpHelper = mockk()

    @Test
    fun shouldLoadTheTrackerListOnceAndKeepIt() {
        every { httpHelper.getPage(TorrentService.TRACKER_LIST_URL) } returns
                "http://tracker.one/announce\n\nhttp://tracker.two:80/announce\r\nnot a tracker\n"
        val service = TorrentService(httpHelper)

        repeat(5) { assertThat(service.trackerUrls).containsExactly("http://tracker.one/announce", "http://tracker.two:80/announce") }

        verify(exactly = 1) { httpHelper.getPage(TorrentService.TRACKER_LIST_URL) }
    }

    @Test
    fun shouldUseTheDefaultTrackersIfTheListCanNotBeLoaded() {
        every { httpHelper.getPage(TorrentService.TRACKER_LIST_URL) } returns ""
        val service = TorrentService(httpHelper)

        assertThat(service.trackerUrls).isEqualTo(TorrentService.DEFAULT_TRACKERS)
        assertThat(service.trackerUrls).isNotEmpty
        // not asked again right away
        verify(exactly = 1) { httpHelper.getPage(TorrentService.TRACKER_LIST_URL) }
    }

    @Test
    fun shouldNeverBeEmptyWithoutAHttpHelper() {
        assertThat(TorrentService(null).trackerUrls).isEqualTo(TorrentService.DEFAULT_TRACKERS)
    }

    @Test
    fun shouldReadTheReleaseTagsOnlyOnce() {
        val service = TorrentService(null)

        assertThat(service.getReleaseTags()).isNotEmpty
        assertThat(service.getReleaseTags()).isSameAs(service.getReleaseTags())
    }
}
