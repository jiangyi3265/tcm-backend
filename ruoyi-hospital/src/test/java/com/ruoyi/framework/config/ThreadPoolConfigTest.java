package com.ruoyi.framework.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class ThreadPoolConfigTest
{
    @Test
    void threadPoolBeans_shouldUseTighterDefaults()
    {
        ThreadPoolConfig config = new ThreadPoolConfig();

        ThreadPoolTaskExecutor threadPoolTaskExecutor = config.threadPoolTaskExecutor();
        threadPoolTaskExecutor.initialize();
        ThreadPoolExecutor executor = threadPoolTaskExecutor.getThreadPoolExecutor();

        assertEquals(16, executor.getCorePoolSize());
        assertEquals(32, executor.getMaximumPoolSize());
        assertEquals(200, executor.getQueue().remainingCapacity());
        assertEquals(60L, executor.getKeepAliveTime(TimeUnit.SECONDS));
        assertTrue(executor.allowsCoreThreadTimeOut());
        assertTrue(executor.getRejectedExecutionHandler() instanceof ThreadPoolExecutor.CallerRunsPolicy);

        ScheduledExecutorService scheduledExecutorService = config.scheduledExecutorService();
        assertTrue(scheduledExecutorService instanceof ScheduledThreadPoolExecutor);
        ScheduledThreadPoolExecutor scheduledThreadPoolExecutor = (ScheduledThreadPoolExecutor) scheduledExecutorService;
        assertEquals(4, scheduledThreadPoolExecutor.getCorePoolSize());
        assertTrue(scheduledThreadPoolExecutor.getRejectedExecutionHandler() instanceof ThreadPoolExecutor.CallerRunsPolicy);

        executor.shutdownNow();
        scheduledThreadPoolExecutor.shutdownNow();
    }
}
