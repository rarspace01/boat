package boat.repositories

import boat.model.Transfer
import org.springframework.data.mongodb.repository.MongoRepository

interface TransferRepository: MongoRepository<Transfer, String> {

}