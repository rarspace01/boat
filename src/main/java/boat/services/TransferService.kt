package boat.services

import boat.model.Transfer
import boat.repositories.TransferRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class TransferService(private val transferRepository: TransferRepository){

    fun save(transfer: Transfer){
        transfer.updated= Instant.now()
        transferRepository.save(transfer)
    }

    fun delete(transfer: Transfer) {
        log.info("Deleting Transfer: {}", transfer)
        transferRepository.deleteById(transfer.id)
    }

    fun getAll():List<Transfer>{
        return transferRepository.findAll()
    }

    companion object {
        private val log = LoggerFactory.getLogger(TransferService::class.java)
    }

}