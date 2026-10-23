package boat.info

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal class CloudServiceTokenTest {

    private val cloudFileService: CloudFileService = mockk(relaxed = true)
    private var now = 0L
    private val cloudService = CloudService(cloudFileService, mockk(relaxed = true)) { now }

    @Test
    fun shouldRememberAnInvalidTokenForAMinuteAndAValidOneForFive() {
        every { cloudFileService.getFilesInPath(any()) } returns emptyList()

        assertThat(cloudService.isCloudTokenValid).isFalse
        now += TimeUnit.SECONDS.toNanos(59)
        assertThat(cloudService.isCloudTokenValid).isFalse
        verify(exactly = 1) { cloudFileService.getFilesInPath(any()) }

        every { cloudFileService.getFilesInPath(any()) } returns listOf("Movies/A/Avatar.mkv")
        now += TimeUnit.SECONDS.toNanos(1)
        assertThat(cloudService.isCloudTokenValid).isTrue
        now += TimeUnit.MINUTES.toNanos(4)
        assertThat(cloudService.isCloudTokenValid).isTrue
        verify(exactly = 2) { cloudFileService.getFilesInPath(any()) }

        now += TimeUnit.MINUTES.toNanos(1)
        assertThat(cloudService.isCloudTokenValid).isTrue
        verify(exactly = 3) { cloudFileService.getFilesInPath(any()) }
    }

    @Test
    fun shouldNotWaitForACheckWhichIsStillRunning() {
        val listing = CountDownLatch(1)
        val release = CountDownLatch(1)
        every { cloudFileService.getFilesInPath(any()) } answers {
            listing.countDown()
            release.await()
            listOf("Movies/A/Avatar.mkv")
        }

        val first = Thread { cloudService.isCloudTokenValid }.apply { start() }
        listing.await()
        assertThat(cloudService.isCloudTokenValid).isFalse
        release.countDown()
        first.join()

        assertThat(cloudService.isCloudTokenValid).isTrue
        verify(exactly = 1) { cloudFileService.getFilesInPath(any()) }
    }
}
