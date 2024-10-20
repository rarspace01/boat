package boat

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.scheduling.annotation.EnableScheduling
import java.util.*
import java.util.concurrent.TimeUnit

@SpringBootApplication
@EnableAutoConfiguration
@EnableScheduling
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
            val ctx: ApplicationContext = SpringApplication.run(Application::class.java, *args)

            println("Let's inspect the beans provided by Spring Boot:")

            val beanNames = ctx.beanDefinitionNames
            Arrays.sort(beanNames)
            for (beanName in beanNames) {
                println(beanName)
            }
        }
    }
}
