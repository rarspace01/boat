package pirateboat.multifileHoster

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import pirateboat.utilities.HttpHelper

class PremiumizeTest {

    private val premiumize: Premiumize = Premiumize(HttpHelper())

    @Test
    fun getRemainingTrafficInMB() {
        // Given
        // When
        val remainingTrafficInMB = premiumize.getRemainingTrafficInMB()
        // Then
        assertThat(remainingTrafficInMB).isNotEqualTo(0.0)
    }
}