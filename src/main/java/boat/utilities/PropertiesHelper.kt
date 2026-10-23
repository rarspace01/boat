package boat.utilities

import java.io.File
import java.io.FileInputStream
import java.util.*

object PropertiesHelper {
    private val logger by LoggerDelegate()

    const val PROPERTIY_FILE: String = "boat.cfg"
    const val PROPERTIY_FILE_DEFAULT: String = "boat.default.cfg"
    const val VERSION_FILE: String = "version.properties"
    const val CONFIG_FILE_PROPERTY: String = "boat.config"

    val version: String?
        get() = runCatching {
            PropertiesHelper::class.java.classLoader.getResourceAsStream(VERSION_FILE)?.use { inputStream ->
                Properties().apply { load(inputStream) }.getProperty("version")
            } ?: "version-missing"
        }.onFailure { logger.warn("could not read {}: {}", VERSION_FILE, it.message) }.getOrNull()

    fun getProperty(propname: String, default: String? = null): String? {
        System.getenv(propname)?.let { return it }

        val value = propertyFile?.let { path ->
            runCatching {
                FileInputStream(path).use { inputStream ->
                    Properties().apply { load(inputStream) }.getProperty(propname)
                }
            }.getOrNull()
        }
        if (value == null) logger.debug("'{}' not found, using '{}'", propname, default)
        return value ?: default
    }

    /** For settings the application can't work without, so a missing one fails at startup with a clear message. */
    fun requireProperty(propname: String): String =
        getProperty(propname)?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("'$propname' is not set, set it in $PROPERTIY_FILE or as environment variable")

    /**
     * [PROPERTIY_FILE_DEFAULT] only documents the settings, its dummy values must not count as set. The system property
     * `boat.config` names another file (the offline tests use their own).
     */
    private val propertyFile: String?
        get() = (System.getProperty(CONFIG_FILE_PROPERTY) ?: PROPERTIY_FILE).takeIf { File(it).isFile }
}
