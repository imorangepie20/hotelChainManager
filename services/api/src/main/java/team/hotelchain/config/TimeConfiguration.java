package team.hotelchain.config;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
public class TimeConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean(defaultCandidate = false)
    @ConditionalOnProperty(name = "website.media.variant-job-enabled", havingValue = "true", matchIfMissing = true)
    ThreadPoolTaskScheduler websiteMediaVariantScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("website-media-variant-");
        return scheduler;
    }
}
