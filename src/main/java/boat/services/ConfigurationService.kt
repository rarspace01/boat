package boat.services

import boat.model.Mode
import boat.utilities.PropertiesHelper
import org.springframework.stereotype.Service

@Service
class ConfigurationService {

    private final var serviceMode:Mode = Mode.BOTH

    init {

            try {
                serviceMode = Mode.valueOf(PropertiesHelper.getProperty("MODE"))
            } catch (exception:Exception) {
                println("Mode setting not valid, default is BOTH")
            }
    }

    fun isSearchMode():Boolean {
        return listOf(Mode.BOTH, Mode.SEARCH).contains(serviceMode)
    }

    fun isDownloadMode():Boolean {
        return listOf(Mode.BOTH, Mode.DOWNLOAD).contains(serviceMode)
    }

}