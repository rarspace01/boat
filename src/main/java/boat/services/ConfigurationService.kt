package boat.services

import boat.model.Mode
import boat.utilities.LoggerDelegate
import boat.utilities.PropertiesHelper
import org.springframework.stereotype.Service

@Service
class ConfigurationService {

    private val logger by LoggerDelegate()

    private final var serviceMode:Mode = Mode.BOTH

    init {

            try {
                serviceMode = Mode.valueOf(PropertiesHelper.getProperty("MODE") ?: "BOTH")
            } catch (_:Exception) {
                logger.warn("Mode setting not valid, default is BOTH")
            }
    }

    fun isSearchMode():Boolean {
        return listOf(Mode.BOTH, Mode.SEARCH).contains(serviceMode)
    }

    fun isDownloadMode():Boolean {
        return listOf(Mode.BOTH, Mode.DOWNLOAD).contains(serviceMode)
    }

    fun getServiceMode():Mode {
        return serviceMode
    }

}