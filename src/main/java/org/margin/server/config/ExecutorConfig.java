package org.margin.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class ExecutorConfig {

    @Bean(name = "wsDbExecutor")
    public Executor wsDbExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Runtime.getRuntime().availableProcessors() * 2);
        executor.setMaxPoolSize(Runtime.getRuntime().availableProcessors() * 4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("ws-db-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}