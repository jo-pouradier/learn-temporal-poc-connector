package com.example.temporal.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Configures a multi-threaded task scheduler for @Scheduled methods.
 * By default, Spring uses a single-threaded scheduler which can cause
 * issues when multiple scheduled tasks need to run concurrently.
 */
@Configuration
public class SchedulingConfig {

    private static final Logger LOG = LoggerFactory.getLogger(SchedulingConfig.class);

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4); // Allow up to 4 concurrent scheduled tasks
        scheduler.setThreadNamePrefix("scheduler-");
        scheduler.setErrorHandler(throwable -> 
            LOG.error("Scheduled task error: {}", throwable.getMessage(), throwable));
        scheduler.initialize();
        LOG.info("Initialized multi-threaded TaskScheduler with pool size: 4");
        return scheduler;
    }
}
