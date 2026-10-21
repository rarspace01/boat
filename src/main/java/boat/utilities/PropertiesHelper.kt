package boat.utilities

import java.io.File
import java.io.FileInputStream
import java.util.*

object PropertiesHelper {
    const val PROPERTIY_FILE: String = "boat.cfg"
    const val PROPERTIY_FILE_DEFAULT: String = "boat.default.cfg"
    const val VERSION_FILE: String = "version.properties"

    val version: String?
        get() = runCatching {
            PropertiesHelper::class.java.classLoader.getResourceAsStream(VERSION_FILE)?.use { inputStream ->
                Properties().apply { load(inputStream) }.getProperty("version")
            } ?: "version-missing"
        }.onFailure { println("Exception: $it") }.getOrNull()

    fun getProperty(propname: String, default: String? = null): String? {
        System.getenv(propname)?.let { return it }

        val value = propertyFile?.let { path ->
            runCatching {
                FileInputStream(path).use { inputStream ->
                    Properties().apply { load(inputStream) }.getProperty(propname)
                }
            }.getOrNull()
        }
        if (value == null) println("'$propname' Notfound, using :'$default'")
        return value ?: default
    }

    /** For settings the application can't work without, so a missing one fails at startup with a clear message. */
    fun requireProperty(propname: String): String =
        getProperty(propname)?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("'$propname' is not set, set it in $PROPERTIY_FILE or as environment variable")

    private val propertyFile: String?
        get() = when {
            File(PROPERTIY_FILE).isFile -> PROPERTIY_FILE
            File(PROPERTIY_FILE_DEFAULT).isFile -> PROPERTIY_FILE_DEFAULT
            else -> null
        }
}
