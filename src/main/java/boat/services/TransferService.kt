package boat.services

import boat.model.Transfer
import boat.repositories.TransferRepository
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class TransferService(private val transferRepository: TransferRepository){

    fun save(transfer: Transfer){
        transfer.updated= Instant.now()
        transferRepository.save(transfer)
    }

    fun delete(transfer:Transfer){
        transferRepository.deleteById(transfer.id)
    }

    fun getAll():List<Transfer>{
        return transferRepository.findAll()
    }

}