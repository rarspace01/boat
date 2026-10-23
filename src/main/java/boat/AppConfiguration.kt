package boat

import boat.utilities.PropertiesHelper.requireProperty
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.MongoDatabaseFactory
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory
import org.springframework.scheduling.annotation.SchedulingConfigurer
import org.springframework.scheduling.config.ScheduledTaskRegistrar
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

@Configuration
class AppConfiguration : SchedulingConfigurer {

    @Bean
    fun mongoClient(): MongoClient {
        return MongoClients.create(requireProperty("MONGO_URI"))
    }

    @Bean
    fun mongoDatabaseFactory(): MongoDatabaseFactory {
        return SimpleMongoClientDatabaseFactory(mongoClient(), "boat")
    }

    override fun configureTasks(taskRegistrar: ScheduledTaskRegistrar) {
        taskRegistrar.setScheduler(scheduler())
    }

    /** Starts the jobs, the `@Async` ones run on [taskExecutor]. */
    @Bean(destroyMethod = "shutdown")
    fun scheduler(): ScheduledExecutorService = Executors.newScheduledThreadPool(SCHEDULER_THREADS)

    /**
     * The `@Async` jobs, each on a (virtual) thread of its own: an upload takes hours and must not hold up the other
     * jobs. A job which must not overlap with its own last run checks that itself ([DownloadMonitor]).
     */
    @Bean(name = ["taskExecutor"], destroyMethod = "shutdown")
    fun taskExecutor(): ExecutorService = Executors.newVirtualThreadPerTaskExecutor()

    private companion object {
        const val SCHEDULER_THREADS = 2
    }
}