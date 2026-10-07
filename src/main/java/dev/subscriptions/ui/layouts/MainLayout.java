package dev.subscriptions.ui.layouts;

import dev.subscriptions.domain.catalogs.Customer;
import dev.subscriptions.domain.catalogs.Tariff;
import dev.subscriptions.domain.documents.Payment;
import dev.subscriptions.domain.documents.Subscription;
import dev.subscriptions.domain.registers.CustomerAccount;
import dev.subscriptions.domain.registers.TariffRevenue;
import org.springframework.stereotype.Component;
import su.onno.ui.Layout;
import su.onno.ui.LayoutSpec;
import su.onno.ui.NavStyle;

@Component
public class MainLayout implements Layout {
    @Override
    public void configure(LayoutSpec layout) {
        layout.shell().nav(NavStyle.SIDEBAR).brand("Подписки");

        layout.section("Обзор")
                .order(0)
                .icon("layout-dashboard")
                .page("/", "Дашборд", "layout-dashboard");

        layout.section("Продажи")
                .order(1)
                .icon("repeat")
                .document(Subscription.class)
                .document(Payment.class);

        layout.section("Справочники")
                .order(2)
                .icon("book-open")
                .catalog(Customer.class)
                .catalog(Tariff.class);

        layout.section("Отчеты")
                .order(3)
                .icon("chart-column")
                .register(CustomerAccount.class)
                .register(TariffRevenue.class);
    }
}
