package boat.multifileHoster

import boat.utilities.HttpHelper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class PremiumizeTest {

    private val premiumize: Premiumize = Premiumize(HttpHelper())

    @Test
    fun getRemainingTrafficInMB() {
        // Given
        // When
        // Then
        assertDoesNotThrow { premiumize.getRemainingTrafficInMB() }
    }
}