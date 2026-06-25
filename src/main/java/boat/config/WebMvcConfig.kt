package boat

import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.converter.ResourceRegionHttpMessageConverter
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebMvcConfig : WebMvcConfigurer {

    @Suppress("DEPRECATION")
    override fun extendMessageConverters(converters: MutableList<HttpMessageConverter<*>>) {
        val regionConverter = ResourceRegionHttpMessageConverter()

        regionConverter.supportedMediaTypes = listOf(
            MediaType.parseMediaType("video/x-matroska"),
            MediaType.parseMediaType("video/mp4"),
            MediaType.parseMediaType("video/x-msvideo"),
            MediaType.parseMediaType("video/quicktime"),
            MediaType.parseMediaType("application/octet-stream"),
            MediaType.ALL
        )

        // This is the clean, non-deprecated way to append to the existing list
        converters.add(0, regionConverter)
    }
}