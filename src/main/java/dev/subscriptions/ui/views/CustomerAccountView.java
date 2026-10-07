package dev.subscriptions.ui.views;

import dev.subscriptions.domain.registers.CustomerAccount;
import dev.subscriptions.ui.Formats;
import org.springframework.stereotype.Component;
import su.onno.ui.EntityConfigBuilder;
import su.onno.ui.EntityView;

@Component
public class CustomerAccountView implements EntityView<CustomerAccount> {
    @Override
    public Class<CustomerAccount> entity() {
        return CustomerAccount.class;
    }

    @Override
    public void fields(EntityConfigBuilder<CustomerAccount> f) {
        f.icon("wallet");
        f.field(CustomerAccount::getCustomer).label("Клиент");
        f.field(CustomerAccount::getAmount).label("Остаток").format(Formats.MONEY);
        f.field("period").label("Дата").format(Formats.DATE_TIME);
    }
}
