package boat

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class DownloadMonitorVersionTest {

    @Test
    fun exactVersionIsUpToDate() {
        assertThat(DownloadMonitor.isVersionUpToDate("1.2.10", "1.2.10")).isTrue
        assertThat(DownloadMonitor.isVersionUpToDate("1.2.10-3-gabc123", "1.2.10")).isTrue
    }

    @Test
    fun prefixOfLocalVersionIsNotUpToDate() {
        assertThat(DownloadMonitor.isVersionUpToDate("1.2.10", "1.2.1")).isFalse
        assertThat(DownloadMonitor.isVersionUpToDate("21", "1")).isFalse
        assertThat(DownloadMonitor.isVersionUpToDate(null, "1")).isFalse
    }
}
