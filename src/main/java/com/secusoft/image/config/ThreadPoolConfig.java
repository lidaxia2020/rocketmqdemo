package com.secusoft.image.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class ThreadPoolConfig {
    @Value("${async.executor.thread.core_pool_size:50}")
    private int corePoolSize;
    @Value("${async.executor.thread.max_pool_size:100}")
    private int maxPoolSize;
    @Value("${async.executor.thread.queue_capacity:1000}")
    private int queueCapacity;
    @Value("${async.executor.thread.keep_alive_seconds:300}")
    private int keepAliveSeconds;
    private ThreadPoolExecutor.CallerRunsPolicy callerRunsPolicy = new ThreadPoolExecutor.CallerRunsPolicy();
    private String threadNamePrefix = "AsyncExecutorThread-";

    public ThreadPoolConfig() {
    }

    @Bean(
            name = {"taskExecutor"}
    )
    public ThreadPoolTaskExecutor asyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(this.corePoolSize);
        executor.setMaxPoolSize(this.maxPoolSize);
        executor.setQueueCapacity(this.queueCapacity);
        executor.setKeepAliveSeconds(this.keepAliveSeconds);
        executor.setRejectedExecutionHandler(this.callerRunsPolicy);
        executor.setThreadNamePrefix(this.threadNamePrefix);
        executor.setRejectedExecutionHandler(this.callerRunsPolicy);
        executor.initialize();
        return executor;
    }
}