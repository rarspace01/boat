package boat

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import java.util.concurrent.TimeUnit

@SpringBootApplication
@EnableCaching
@ComponentScan("boat", "boat.torrent")
class Application {
    @Bean
    fun caffeineConfig(): Caffeine<*, *> {
        return Caffeine.newBuilder().expireAfterWrite(12, TimeUnit.HOURS)
    }

    @Bean
    fun cacheManager(caffeine: Caffeine<Any, Any>): CacheManager {
        val caffeineCacheManager = CaffeineCacheManager()
        caffeineCacheManager.setCaffeine(caffeine)
        return caffeineCacheManager
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            SpringApplication.run(Application::class.java, *args)
        }
    }
}
