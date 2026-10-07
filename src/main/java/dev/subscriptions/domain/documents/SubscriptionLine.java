package dev.subscriptions.domain.documents;

import dev.subscriptions.domain.catalogs.Tariff;
import lombok.Getter;
import lombok.Setter;
import su.onno.annotations.Attribute;
import su.onno.model.TabularSectionRow;
import su.onno.types.Ref;

import java.math.BigDecimal;

@Getter
@Setter
public class SubscriptionLine extends TabularSectionRow {
    @Attribute(displayName = "Тариф")
    private Ref<Tariff> tariff;

    @Attribute(displayName = "Число периодов")
    private Integer periods = 1;

    @Attribute(displayName = "Цена", precision = 15, scale = 2)
    private BigDecimal price = BigDecimal.ZERO;

    @Attribute(displayName = "Сумма", precision = 15, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;
}
