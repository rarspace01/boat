package boat

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class SecurityConfigurationTest {

    @Test
    fun shouldAllowOnlyTheConfirmationOfTheDeleteButtonAsScript() {
        // the sha256 of BoatController.CONFIRM_DELETE_SCRIPT as browsers compute it of the onsubmit attribute (Python's
        // hashlib), update it when the text changes
        assertThat(SecurityConfiguration.CONTENT_SECURITY_POLICY)
            .contains("script-src 'unsafe-hashes' 'sha256-KJdddbuLVORNv/0OoRD0PUdaZ12zzA9lHi0L5rQaDCA='")
            .contains("default-src 'none'", "form-action 'self'", "frame-ancestors 'none'")
        assertThat(SecurityConfiguration.CONTENT_SECURITY_POLICY.substringAfter("script-src").substringBefore(";"))
            .doesNotContain("'unsafe-inline'")
    }

    @Test
    fun shouldUseTheAllowedScriptOnTheDeleteButton() {
        val html = BoatController.transferControls(boat.model.Transfer(id = "1"))

        assertThat(html).contains("onsubmit=\"${BoatController.CONFIRM_DELETE_SCRIPT}\"")
    }
}
