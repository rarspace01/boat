package boat

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.http.converter.ResourceRegionHttpMessageConverter
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebMvcConfig : WebMvcConfigurer {

    @Bean
    @Primary
    fun resourceRegionHttpMessageConverter(): ResourceRegionHttpMessageConverter {
        val regionConverter = ResourceRegionHttpMessageConverter()

        // Explicitly set the supported media types
        regionConverter.supportedMediaTypes = listOf(
            MediaType.parseMediaType("video/x-matroska"),
            MediaType.parseMediaType("video/mp4"),
            MediaType.parseMediaType("video/x-msvideo"),
            MediaType.parseMediaType("video/quicktime"),
            MediaType.parseMediaType("application/octet-stream"),
            MediaType.ALL
        )

        return regionConverter
    }
}