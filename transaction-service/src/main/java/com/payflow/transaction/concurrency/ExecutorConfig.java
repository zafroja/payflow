package com.payflow.transaction.concurrency;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * A dedicated, bounded pool for bulk transfers.
 * <ul>
 *   <li>Bounded queue + CallerRunsPolicy = back-pressure: when the pool is saturated the request
 *       thread runs the task itself instead of queueing unbounded work (no OutOfMemoryError).</li>
 *   <li>Separate from Tomcat's request threads and from ForkJoinPool.commonPool, so blocking
 *       HTTP/DB calls cannot starve the rest of the application.</li>
 *   <li>Keep max-pool-size below the Hikari pool size so tasks do not wait for DB connections.</li>
 * </ul>
 */
@Configuration
public class ExecutorConfig {

    public static final String TRANSFER_EXECUTOR = "transferExecutor";

    @Bean(name = TRANSFER_EXECUTOR)
    public ThreadPoolTaskExecutor transferExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(12);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("transfer-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setTaskDecorator(mdcPropagation());
        executor.setWaitForTasksToCompleteOnShutdown(true); // graceful shutdown on pod termination
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }

    /** ThreadLocals (MDC, SecurityContext) do not cross thread boundaries on their own: copy MDC explicitly. */
    private static TaskDecorator mdcPropagation() {
        return runnable -> {
            Map<String, String> context = MDC.getCopyOfContextMap();
            return () -> {
                Map<String, String> previous = MDC.getCopyOfContextMap();
                if (context != null) {
                    MDC.setContextMap(context);
                }
                try {
                    runnable.run();
                } finally {
                    if (previous != null) {
                        MDC.setContextMap(previous);
                    } else {
                        MDC.clear();
                    }
                }
            };
        };
    }
}
