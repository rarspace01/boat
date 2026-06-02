package boat

import boat.utilities.PropertiesHelper.getProperty
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.MongoDatabaseFactory
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory
import org.springframework.scheduling.annotation.SchedulingConfigurer
import org.springframework.scheduling.config.ScheduledTaskRegistrar
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import kotlin.math.max

@Configuration
class AppConfiguration : SchedulingConfigurer {
    @Bean
    fun mongoClient(): MongoClient {
        return MongoClients.create(getProperty("MONGO_URI") ?: "")
    }

    @Bean
    fun mongoDatabaseFactory(): MongoDatabaseFactory {
        return SimpleMongoClientDatabaseFactory(mongoClient(), "boat")
    }

    override fun configureTasks(taskRegistrar: ScheduledTaskRegistrar) {
        taskRegistrar.setScheduler(taskExecutor())
    }

    @Bean(destroyMethod = "shutdown")
    fun taskExecutor(): ScheduledExecutorService {
        val scheduledPoolSize = max(Runtime.getRuntime().availableProcessors(), 2)
        println("scheduledPoolSize:$scheduledPoolSize")
        return Executors.newScheduledThreadPool(scheduledPoolSize)
    }
}