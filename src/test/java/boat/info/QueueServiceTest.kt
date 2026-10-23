package boat.info

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

internal class QueueServiceTest {

    @TempDir
    lateinit var dir: File

    private fun queueService() = QueueService(File(dir, "sub/queue.list"))

    @Test
    fun shouldKeepMatchedMediaDataInTheQueue() {
        val queueService = queueService()
        val item = MediaItem("Avatar", "Avatar", 2009, MediaType.Movie, "{tmdb-19995}")

        queueService.add(item)

        assertThat(queueService.getQueue()).containsExactly(item)
    }

    @Test
    fun shouldKeepUnmatchedItemWithoutYear() {
        val queueService = queueService()

        queueService.add(MediaItem("Unknown"))

        assertThat(queueService.getQueue()).containsExactly(MediaItem("Unknown"))
    }

    @Test
    fun shouldKeepTheOrderOfTheEntriesAlsoInTheFile() {
        val items = (1..30).map { MediaItem("Title $it", year = 2000 + it) } + MediaItem("Amélie 東京 = a:b", year = 2001)
        queueService().addAll(items)

        assertThat(queueService().getQueue()).containsExactlyElementsOf(items)
    }

    @Test
    fun shouldReadTheQueueWhileItIsChanged() {
        val queueService = queueService()
        val writer = Thread { repeat(2000) { queueService.add(MediaItem("Title $it")) } }.apply { start() }
        repeat(200) { queueService.getQueue() }
        writer.join()

        assertThat(queueService.getQueue()).hasSize(2000)
    }

    @Test
    fun shouldKeepSameTitleWithDifferentYears() {
        val queueService = queueService()
        val first = MediaItem("Dune", year = 1984)
        val second = MediaItem("Dune", year = 2021)

        queueService.addAll(listOf(first, second))
        assertThat(queueService.getQueue()).containsExactlyInAnyOrder(first, second)

        queueService.remove(first)
        assertThat(queueService.getQueue()).containsExactly(second)
    }

    @Test
    fun shouldCreateMissingDirectoryAndReloadSavedQueue() {
        val item = MediaItem("Avatar", year = 2009, type = MediaType.Movie, id = "{tmdb-19995}")
        queueService().addAll(listOf(item))

        assertThat(queueService().getQueue()).containsExactly(item)
    }

    @Test
    fun shouldReadAndRemoveEntriesOfOlderQueueFiles() {
        File(dir, "queue.list").writeText("Avatar=2009\nNo\\ Year=null\n")
        val queueService = QueueService(File(dir, "queue.list"))

        assertThat(queueService.getQueue()).containsExactlyInAnyOrder(MediaItem("Avatar", year = 2009), MediaItem("No Year"))

        queueService.remove(MediaItem("Avatar", year = 2009))
        assertThat(queueService.getQueue()).containsExactly(MediaItem("No Year"))
    }
}
