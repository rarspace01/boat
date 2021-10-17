package boat.services

import boat.model.Transfer
import boat.repositories.TransferRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.Optional

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

    fun get(transfer: Transfer): Optional<Transfer> {
        return transferRepository.findById(transfer.id)
    }

    fun get(transfer: Optional<Transfer>): Optional<Transfer> {
        return if(transfer.isPresent){
            get(transfer)
        } else {
            Optional.empty()
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(TransferService::class.java)
    }

}