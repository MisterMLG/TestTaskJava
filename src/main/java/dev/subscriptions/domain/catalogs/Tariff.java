package dev.subscriptions.domain.catalogs;

import lombok.Getter;
import lombok.Setter;
import su.onno.annotations.AccessControl;
import su.onno.annotations.Attribute;
import su.onno.annotations.Catalog;
import su.onno.model.CatalogObject;
import su.onno.rules.BusinessRule;
import su.onno.rules.Validated;

import java.math.BigDecimal;
import java.util.List;

@Catalog(name = "Tariffs", title = "Тарифы", codePrefix = "T-", context = "Subscriptions")
@AccessControl(readRoles = {"MANAGER"}, writeRoles = {"MANAGER"})
@Getter
@Setter
public class Tariff extends CatalogObject implements Validated {
    @Attribute(displayName = "Цена за период", precision = 15, scale = 2)
    private BigDecimal pricePerPeriod;

    @Attribute(displayName = "Длительность периода, дней")
    private Integer periodDays = 30;

    @Attribute(displayName = "Доступен для подключения")
    private Boolean available = true;

    @Override
    public List<BusinessRule> rules() {
        return List.of(
                BusinessRule.onField("description", "Укажите наименование тарифа",
                        () -> getDescription() != null && !getDescription().isBlank()),
                BusinessRule.onField("pricePerPeriod", "Цена за период должна быть больше нуля",
                        () -> pricePerPeriod != null && pricePerPeriod.signum() > 0),
                BusinessRule.onField("periodDays", "Длительность периода должна быть больше нуля",
                        () -> periodDays != null && periodDays > 0));
    }
}
