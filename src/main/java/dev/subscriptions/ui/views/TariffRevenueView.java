package dev.subscriptions.ui.views;

import dev.subscriptions.domain.registers.TariffRevenue;
import dev.subscriptions.ui.Formats;
import org.springframework.stereotype.Component;
import su.onno.ui.EntityConfigBuilder;
import su.onno.ui.EntityView;

@Component
public class TariffRevenueView implements EntityView<TariffRevenue> {
    @Override
    public Class<TariffRevenue> entity() {
        return TariffRevenue.class;
    }

    @Override
    public void fields(EntityConfigBuilder<TariffRevenue> f) {
        f.icon("chart-column");
        f.field(TariffRevenue::getTariff).label("Тариф");
        f.field(TariffRevenue::getCustomer).label("Клиент");
        f.field(TariffRevenue::getAmount).label("Выручка").format(Formats.MONEY);
        f.field(TariffRevenue::getPeriods).label("Периодов").format("integer");
        f.field("period").label("Дата").format(Formats.DATE_TIME);
    }
}
