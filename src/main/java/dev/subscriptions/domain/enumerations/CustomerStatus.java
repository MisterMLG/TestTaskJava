package dev.subscriptions.domain.enumerations;

import su.onno.annotations.EnumLabel;
import su.onno.annotations.Enumeration;

@Enumeration(name = "CustomerStatuses", title = "Статус клиента")
public enum CustomerStatus {
    @EnumLabel(value = "Активен", color = "#059669") ACTIVE,
    @EnumLabel(value = "Заблокирован", color = "#DC2626") BLOCKED
}
