package org.margin.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
public class ExecutorConfig {
    @Bean(name = "wsDbExecutor")
    public Executor wsDbExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}