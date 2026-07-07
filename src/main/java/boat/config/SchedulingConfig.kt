package boat.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

@Configuration
@ConditionalOnProperty(name = ["boat.scheduling.enabled"], havingValue = "true", matchIfMissing = true)
@EnableScheduling
class SchedulingConfig
