package boat.multifileHoster

import boat.utilities.HttpHelper
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class MultifileHosterRegistrationTest {

    private val httpHelper: HttpHelper = mockk(relaxed = true)

    @Test
    fun shouldUseAlldebridOnlyWithAnApiKey() {
        assertThat(MultifileHosterService.createMultifileHosters(httpHelper) { null }.map { it.getName() })
            .containsExactly("Premiumize")
        assertThat(MultifileHosterService.createMultifileHosters(httpHelper) { " " }.map { it.getName() })
            .containsExactly("Premiumize")
        assertThat(MultifileHosterService.createMultifileHosters(httpHelper) { if (it == "ALLDEBRID_APIKEY") "key" else null }.map { it.getName() })
            .containsExactly("Premiumize", "Alldebrid")
    }
}
