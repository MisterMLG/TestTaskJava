package dev.subscriptions.domain.registers;

import dev.subscriptions.domain.catalogs.Customer;
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

@AccumulationRegister(name = "CustomerAccounts", title = "Лицевые счета",
        type = AccumulationType.BALANCE, context = "Subscriptions")
@AccessControl(readRoles = {"MANAGER"})
@Getter
@Setter
public class CustomerAccount extends AccumulationRecord {
    @Dimension(displayName = "Клиент")
    private Ref<Customer> customer;

    @Resource(displayName = "Сумма", precision = 15, scale = 2)
    private BigDecimal amount;
}
