package boat;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import boat.utilities.PropertiesHelper;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class AppConfiguration implements SchedulingConfigurer {

    /*
     * Use the standard Mongo driver API to create a com.mongodb.client.MongoClient instance.
     */
    public @Bean
    MongoClient mongoClient() {
        MongoClient mongoClient = MongoClients.create(PropertiesHelper.getProperty("MONGO_URI"));
        return mongoClient;
    }

    public @Bean
    MongoDatabaseFactory mongoDatabaseFactory() {
        return new SimpleMongoClientDatabaseFactory(mongoClient(), "boat");
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setScheduler(taskExecutor());
    }

    @Bean(destroyMethod = "shutdown")
    public Executor taskExecutor() {
        int scheduledPoolSize = Math.min(Runtime.getRuntime().availableProcessors(), 2);
        log.info("scheduledPoolSize:{}", scheduledPoolSize);
        return Executors.newScheduledThreadPool(scheduledPoolSize);
    }
}