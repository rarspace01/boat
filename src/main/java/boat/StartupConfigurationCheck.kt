package boat

import boat.info.CloudService
import boat.utilities.LoggerDelegate
import boat.utilities.PropertiesHelper
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component

/**
 * Stops the start with a clear message if a setting is missing that would otherwise end in paths like `null/Movies`
 * later on, and warns about missing api keys (the features using them just don't work).
 */
@Component
class StartupConfigurationCheck {

    @PostConstruct
    fun check() {
        val result = validate(PropertiesHelper::getProperty)
        result.warnings.forEach { logger.warn(it) }
        check(result.errors.isEmpty()) { result.errors.joinToString("; ") }
    }

    class Result(val errors: List<String>, val warnings: List<String>)

    internal fun validate(lookup: (String) -> String?): Result {
        fun isMissing(name: String) = lookup(name).isNullOrBlank()
        val errors = REQUIRED.filter { isMissing(it) }
            .map { "'$it' is not set, set it in ${PropertiesHelper.PROPERTIY_FILE} or as environment variable" }
        val warnings = mutableListOf<String>()
        API_KEYS.filter { isMissing(it) }.forEach { warnings += "'$it' is not set, the features using it won't work" }
        if (HOSTER_API_KEYS.all { isMissing(it) }) {
            warnings += "none of ${HOSTER_API_KEYS.joinToString()} is set, there is no multifilehoster to download with"
        }
        return Result(errors, warnings)
    }

    companion object {
        private val logger by LoggerDelegate()
        private val REQUIRED = listOf("MONGO_URI", CloudService.RCLONE_DIR)
        private val HOSTER_API_KEYS = listOf("PREMIUMIZE_APIKEY", "ALLDEBRID_APIKEY")
        private val API_KEYS = listOf("TFDB_APIKEY", "TVDB_APIKEY")
    }
}
