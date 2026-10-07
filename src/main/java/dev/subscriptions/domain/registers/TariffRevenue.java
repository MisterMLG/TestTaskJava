package dev.subscriptions.domain.registers;

import dev.subscriptions.domain.catalogs.Customer;
import dev.subscriptions.domain.catalogs.Tariff;
import lombok.Getter;
import lombok.Setter;
import su.onno.annotations.AccessControl;
import su.onno.annotations.AccumulationRegister;
import su.onno.annotations.Dimension;
import su.onno.annotations.Resource;
import su.onno.model.AccumulationRecord;
import su.onno.model.AccumulationType;
import su.onno.types.Ref;

import java.math.BigDecimal;

@AccumulationRegister(name = "TariffRevenue", title = "Выручка по тарифам",
        type = AccumulationType.TURNOVER, context = "Subscriptions")
@AccessControl(readRoles = {"MANAGER"})
@Getter
@Setter
public class TariffRevenue extends AccumulationRecord {
    @Dimension(displayName = "Тариф")
    private Ref<Tariff> tariff;

    @Dimension(displayName = "Клиент")
    private Ref<Customer> customer;

    @Resource(displayName = "Сумма", precision = 15, scale = 2)
    private BigDecimal amount;

    @Resource(displayName = "Периодов", precision = 10, scale = 0)
    private BigDecimal periods;
}
