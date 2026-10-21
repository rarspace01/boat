package boat

import boat.model.Transfer
import boat.model.TransferStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

internal class DownloadMonitorCleanupTest {

    private val now = Instant.now()

    private fun transfer(status: TransferStatus, ageInDays: Long) =
        Transfer(transferStatus = status, updated = now.minus(ageInDays, ChronoUnit.DAYS).minusSeconds(60))

    @Test
    fun shouldDeleteTransfersWithErrorsRightAway() {
        assertThat(DownloadMonitor.isTransferObsolete(transfer(TransferStatus.ERROR, 0), now)).isTrue
        assertThat(DownloadMonitor.isTransferObsolete(transfer(TransferStatus.DUPLICATE_ERROR, 0), now)).isTrue
    }

    @Test
    fun shouldDeleteStaleTransfersAfterADay() {
        assertThat(DownloadMonitor.isTransferObsolete(transfer(TransferStatus.DOWNLOADING_TO_MULTIHOSTER, 0), now)).isFalse
        assertThat(DownloadMonitor.isTransferObsolete(transfer(TransferStatus.DOWNLOADING_TO_MULTIHOSTER, 1), now)).isTrue
    }

    @Test
    fun shouldKeepTransfersWaitingForTrafficLonger() {
        assertThat(DownloadMonitor.isTransferObsolete(transfer(TransferStatus.ADDED, 3), now)).isFalse
        assertThat(DownloadMonitor.isTransferObsolete(transfer(TransferStatus.ADDED, 7), now)).isTrue
    }
}
