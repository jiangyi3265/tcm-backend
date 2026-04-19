package com.ruoyi.hospital.service.impl;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.ruoyi.hospital.service.ITcmAppointmentNotificationService;

@Component
public class TcmAppointmentNotificationScheduler
{
    private static final Logger log = LoggerFactory.getLogger(TcmAppointmentNotificationScheduler.class);

    private final ITcmAppointmentNotificationService appointmentNotificationService;

    @Resource(name = "scheduledExecutorService")
    private ScheduledExecutorService scheduledExecutorService;

    private ScheduledFuture<?> future;

    public TcmAppointmentNotificationScheduler(ITcmAppointmentNotificationService appointmentNotificationService)
    {
        this.appointmentNotificationService = appointmentNotificationService;
    }

    @PostConstruct
    public void start()
    {
        future = scheduledExecutorService.scheduleAtFixedRate(() -> {
            try
            {
                appointmentNotificationService.processDueNotifications();
            }
            catch (Exception e)
            {
                log.warn("Failed to process appointment notifications", e);
            }
        }, 60, 300, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void stop()
    {
        if (future != null)
        {
            future.cancel(false);
        }
    }
}
