package com.example.dependencyscanner.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
@EnableRabbit
public class ScannerConfig {
    @Bean
    RestClient osvRestClient(@Value("${scanner.osv-url}") String url) {
        return RestClient.builder().baseUrl(url).build();
    }

    @Bean(name = "scanExecutor")
    Executor scanExecutor(@Value("${scanner.async-threads:4}") int threads) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("dependency-scan-");
        executor.initialize();
        return executor;
    }
}
