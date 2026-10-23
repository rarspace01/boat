package boat.repositories

import boat.model.QueueEntry
import org.springframework.data.mongodb.repository.MongoRepository

interface QueueRepository : MongoRepository<QueueEntry, String> {
    fun findAllByOrderBySequenceAsc(): List<QueueEntry>
}
