package boat

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class StartupConfigurationCheckTest {

    private val check = StartupConfigurationCheck()

    private fun settings(vararg pairs: Pair<String, String>): (String) -> String? = pairs.toMap()::get

    @Test
    fun shouldFailWithoutMongoUriAndRcloneDir() {
        val result = check.validate(settings())

        assertThat(result.errors).hasSize(2)
        assertThat(result.errors.joinToString()).contains("MONGO_URI", "RCLONEDIR")
    }

    @Test
    fun shouldTreatBlankSettingsAsMissing() {
        val result = check.validate(settings("MONGO_URI" to " ", "RCLONEDIR" to "remote:"))

        assertThat(result.errors).hasSize(1)
        assertThat(result.errors.single()).contains("MONGO_URI")
    }

    @Test
    fun shouldOnlyWarnAboutMissingApiKeys() {
        val result = check.validate(settings("MONGO_URI" to "mongodb://localhost", "RCLONEDIR" to "remote:"))

        assertThat(result.errors).isEmpty()
        assertThat(result.warnings.joinToString()).contains("TMDB_APIKEY", "TVDB_APIKEY", "multifilehoster")
    }

    @Test
    fun shouldAcceptTheOldTmdbKeyName() {
        val result = check.validate(
            settings("MONGO_URI" to "mongodb://localhost", "RCLONEDIR" to "remote:", "ALLDEBRID_APIKEY" to "x", "TFDB_APIKEY" to "x", "TVDB_APIKEY" to "x")
        )

        assertThat(result.warnings).isEmpty()
    }

    @Test
    fun shouldAcceptOneHosterKey() {
        val result = check.validate(
            settings("MONGO_URI" to "mongodb://localhost", "RCLONEDIR" to "remote:", "ALLDEBRID_APIKEY" to "x", "TMDB_APIKEY" to "x", "TVDB_APIKEY" to "x")
        )

        assertThat(result.errors).isEmpty()
        assertThat(result.warnings).isEmpty()
    }
}
