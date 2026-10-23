package boat.info

import boat.utilities.PropertiesHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal class CloudServiceTargetFolderTest {

    private val cloudFileService: CloudFileService = mockk(relaxed = true)
    private var now = 0L
    private val cloudService = CloudService(cloudFileService) { now }

    @BeforeEach
    fun setUp() {
        mockkObject(PropertiesHelper)
        every { PropertiesHelper.getProperty(CloudService.RCLONE_DIR) } returns "remote:Library"
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(PropertiesHelper)
    }

    @Test
    fun shouldKeepAReadyFolderForADayAndCheckAFailureAgainAfterAMinute() {
        every { cloudFileService.isDirectoryReachable("remote:Library") } returns false

        assertThat(cloudService.isTargetFolderReady).isFalse
        now += TimeUnit.SECONDS.toNanos(59)
        assertThat(cloudService.isTargetFolderReady).isFalse
        verify(exactly = 1) { cloudFileService.isDirectoryReachable(any()) }

        every { cloudFileService.isDirectoryReachable("remote:Library") } returns true
        now += TimeUnit.SECONDS.toNanos(1)
        assertThat(cloudService.isTargetFolderReady).isTrue
        now += TimeUnit.HOURS.toNanos(23)
        assertThat(cloudService.isTargetFolderReady).isTrue
        verify(exactly = 2) { cloudFileService.isDirectoryReachable(any()) }

        every { cloudFileService.isDirectoryReachable("remote:Library") } returns false
        now += TimeUnit.HOURS.toNanos(1)
        assertThat(cloudService.isTargetFolderReady).isFalse
        verify(exactly = 3) { cloudFileService.isDirectoryReachable(any()) }
    }

    @Test
    fun shouldNotBeReadyWithoutATargetFolder() {
        every { PropertiesHelper.getProperty(CloudService.RCLONE_DIR) } returns null

        assertThat(cloudService.isTargetFolderReady).isFalse
        verify(exactly = 0) { cloudFileService.isDirectoryReachable(any()) }
    }

    @Test
    fun shouldCheckOnTheStart() {
        every { cloudFileService.isDirectoryReachable("remote:Library") } returns true

        cloudService.verifyTargetFolderOnStart()
        assertThat(cloudService.isTargetFolderReady).isTrue

        verify(exactly = 1) { cloudFileService.isDirectoryReachable(any()) }
    }

    @Test
    fun shouldNotWaitForACheckWhichIsStillRunning() {
        val listing = CountDownLatch(1)
        val release = CountDownLatch(1)
        every { cloudFileService.isDirectoryReachable(any()) } answers {
            listing.countDown()
            release.await()
            true
        }

        val first = Thread { cloudService.isTargetFolderReady }.apply { start() }
        listing.await()
        assertThat(cloudService.isTargetFolderReady).isFalse
        release.countDown()
        first.join()

        assertThat(cloudService.isTargetFolderReady).isTrue
        verify(exactly = 1) { cloudFileService.isDirectoryReachable(any()) }
    }
}
