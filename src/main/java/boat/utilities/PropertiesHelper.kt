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

        return propertyFile?.let { path ->
            runCatching {
                FileInputStream(path).use { inputStream ->
                    Properties().apply { load(inputStream) }.getProperty(propname)
                }
            }.onFailure { println("'$propname' Notfound, using :'$default'") }.getOrElse({ default })
        }
    }

    private val propertyFile: String?
        get() = when {
            File(PROPERTIY_FILE).isFile -> PROPERTIY_FILE
            File(PROPERTIY_FILE_DEFAULT).isFile -> PROPERTIY_FILE_DEFAULT
            else -> null
        }
}
