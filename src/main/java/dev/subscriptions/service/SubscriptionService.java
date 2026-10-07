package dev.subscriptions.service;

import dev.subscriptions.domain.documents.Subscription;
import dev.subscriptions.domain.enumerations.SubscriptionStatus;
import dev.subscriptions.repositories.SubscriptionRepository;
import org.springframework.stereotype.Service;
import su.onno.posting.PostingService;

import java.time.LocalDate;
import java.util.UUID;

@Service
public class SubscriptionService {
    private final SubscriptionRepository subscriptions;
    private final PostingService posting;

    public SubscriptionService(SubscriptionRepository subscriptions, PostingService posting) {
        this.subscriptions = subscriptions;
        this.posting = posting;
    }

    public Subscription cancel(UUID id, String reason) {
        Subscription subscription = load(id);
        if (subscription.getStatus() == SubscriptionStatus.CANCELLED) {
            throw new IllegalStateException("Подписка уже отменена");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Укажите причину отмены");
        }
        if (subscription.isPosted()) {
            posting.unpost(subscription);
            subscription = load(id);
        }
        subscription.setStatus(SubscriptionStatus.CANCELLED);
        subscription.setCancelReason(reason.trim());
        return subscriptions.save(subscription);
    }

    public int refreshStatuses(LocalDate today) {
        int changed = 0;
        for (Subscription subscription : subscriptions.findAllActive()) {
            SubscriptionStatus expected = subscription.expectedStatus(today);
            if (subscription.getStatus() != expected) {
                subscription.setStatus(expected);
                subscriptions.save(subscription);
                changed++;
            }
        }
        return changed;
    }

    private Subscription load(UUID id) {
        return subscriptions.findActiveById(id)
                .orElseThrow(() -> new IllegalArgumentException("Подписка не найдена"));
    }
}
