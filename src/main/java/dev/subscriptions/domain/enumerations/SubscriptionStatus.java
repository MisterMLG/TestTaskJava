package dev.subscriptions.domain.enumerations;

import su.onno.annotations.EnumLabel;
import su.onno.annotations.Enumeration;

@Enumeration(name = "SubscriptionStatuses", title = "Статус подписки")
public enum SubscriptionStatus {
    @EnumLabel(value = "Черновик", color = "#6B7280") DRAFT,
    @EnumLabel(value = "Ожидает начала", color = "#2563EB") PENDING,
    @EnumLabel(value = "Активна", color = "#059669") ACTIVE,
    @EnumLabel(value = "Истекла", color = "#D97706") EXPIRED,
    @EnumLabel(value = "Отменена", color = "#DC2626") CANCELLED
}
