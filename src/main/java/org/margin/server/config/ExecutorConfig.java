package org.margin.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class ExecutorConfig {
    @Bean(name = "wsDbExecutor")
    public Executor wsDbExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}