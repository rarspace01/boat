package boat.info

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class QueueServiceTest {

    @Test
    fun shouldKeepMatchedMediaDataInTheQueue() {
        val queueService = QueueService()
        val item = MediaItem("Avatar", "Avatar", 2009, MediaType.Movie, "{tmdb-19995}")

        queueService.add(item)

        assertThat(queueService.getQueue().filter { it.title == "Avatar" }).containsExactly(item)
    }

    @Test
    fun shouldKeepUnmatchedItemWithoutYear() {
        val queueService = QueueService()

        queueService.add(MediaItem("Some Unknown Title Without Year"))

        assertThat(queueService.getQueue().filter { it.title == "Some Unknown Title Without Year" })
            .containsExactly(MediaItem("Some Unknown Title Without Year"))
    }
}
