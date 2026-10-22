package boat.services

import boat.model.Transfer
import boat.repositories.TransferRepository
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Optional

internal class TransferServiceTest {

    private val repository: TransferRepository = mockk()
    private val service = TransferService(repository)

    @Test
    fun shouldFindATransferById() {
        val transfer = Transfer(id = "abc", name = "Dune")
        every { repository.findById("abc") } returns Optional.of(transfer)
        every { repository.findById("missing") } returns Optional.empty()

        assertThat(service.getById("abc")).isSameAs(transfer)
        assertThat(service.getById("missing")).isNull()
    }
}
