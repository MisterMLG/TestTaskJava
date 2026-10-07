package dev.subscriptions.jobs;

import dev.subscriptions.service.SubscriptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import su.onno.annotations.ScheduledJob;
import su.onno.jobs.BackgroundTask;

import java.time.LocalDate;

@Component
@ScheduledJob(name = "SubscriptionStatusRefresh", cron = "0 0 * * * *")
public class SubscriptionStatusJob implements BackgroundTask {
    private static final Logger log = LoggerFactory.getLogger(SubscriptionStatusJob.class);

    private final SubscriptionService subscriptions;

    public SubscriptionStatusJob(SubscriptionService subscriptions) {
        this.subscriptions = subscriptions;
    }

    @Override
    public void execute() {
        int changed = subscriptions.refreshStatuses(LocalDate.now());
        log.info("Статусы подписок актуализированы, изменено: {}", changed);
    }
}
