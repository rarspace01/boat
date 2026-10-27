package boat.info

import boat.model.QueueEntry
import boat.repositories.QueueRepository
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class QueueServiceTest {

    /** The collection of the repository, so a new service on it is a restart. */
    private val stored = LinkedHashMap<String, QueueEntry>()

    private val repository: QueueRepository = mockk<QueueRepository>().also { repo ->
        every { repo.existsById(any()) } answers { stored.containsKey(firstArg()) }
        every { repo.save(any<QueueEntry>()) } answers { firstArg<QueueEntry>().also { stored[it.id] = it } }
        every { repo.deleteById(any()) } answers { stored.remove(firstArg<String>()); Unit }
        every { repo.findAllByOrderBySequenceAsc() } answers { stored.values.sortedBy { it.sequence } }
    }

    private fun queueService() = QueueService(repository)

    @Test
    fun queueSurvivesRestart() {
        val items = (1..50).map { MediaItem("Title $it", year = 2000 + it, type = MediaType.Movie, id = "id$it", originalTitle = "Orig $it") }

        queueService().addAll(items)

        assertThat(queueService().getQueue()).containsExactlyElementsOf(items)
    }

    @Test
    fun addingAgainKeepsPlace() {
        val queueService = queueService()
        queueService.addAll(listOf(MediaItem("A"), MediaItem("B")))
        queueService.add(MediaItem("A"))

        assertThat(queueService.getQueue()).containsExactly(MediaItem("A"), MediaItem("B"))
    }

    @Test
    fun sameTitleWithDifferentYearsDoNotCollide() {
        val queueService = queueService()
        val first = MediaItem("Daca", year = 1984)
        val second = MediaItem("Daca", year = 2021)
        queueService.addAll(listOf(first, second))
        assertThat(queueService.getQueue()).containsExactly(first, second)

        queueService.remove(first)
        assertThat(queueService.getQueue()).containsExactly(second)
    }
}
