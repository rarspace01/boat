package boat.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class PasswordConfigTest {

    private val passwordEncoder = PasswordConfig().passwordEncoder()

    @Test
    fun shouldAcceptTheHashFromTheReadmeCommand() {
        // echo "{bcrypt}$(htpasswd -bnBC 10 "" 'YOUR_PASSWORD' | tr -d ':\n')"
        val hash = "{bcrypt}\$2y\$10\$xrmcArN.WRgD/uoWlci7H.K0kDNyMLotN1eK70ChpesebSBA/dCde"

        assertThat(passwordEncoder.matches("YOUR_PASSWORD", hash)).isTrue
        assertThat(passwordEncoder.matches("wrong", hash)).isFalse
    }
}
