package boat.config

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.http.converter.ResourceRegionHttpMessageConverter
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter
import jakarta.annotation.PostConstruct

@Configuration
class WebMvcConfig {

    @Autowired
    private lateinit var handlerAdapter: RequestMappingHandlerAdapter

    @PostConstruct
    fun addRegionConverter() {
        val regionConverter = ResourceRegionHttpMessageConverter()

        regionConverter.supportedMediaTypes = listOf(
            MediaType.parseMediaType("video/x-matroska"),
            MediaType.parseMediaType("video/mp4"),
            MediaType.parseMediaType("video/x-msvideo"),
            MediaType.parseMediaType("video/quicktime"),
            MediaType.parseMediaType("application/octet-stream"),
            MediaType.ALL
        )

        // Forcefully inject into the front of the adapter's converter list
        val converters = handlerAdapter.messageConverters.toMutableList()
        converters.add(0, regionConverter)
        handlerAdapter.messageConverters = converters
    }
}