package org.margin.server.email;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
public class MailExecutorConfig {

    @Bean
    public Executor mailExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
