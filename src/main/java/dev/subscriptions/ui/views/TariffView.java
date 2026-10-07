package dev.subscriptions.ui.views;

import dev.subscriptions.domain.catalogs.Tariff;
import dev.subscriptions.ui.Formats;
import org.springframework.stereotype.Component;
import su.onno.ui.EntityConfigBuilder;
import su.onno.ui.EntityView;
import su.onno.ui.ListSpec;

@Component
public class TariffView implements EntityView<Tariff> {
    @Override
    public Class<Tariff> entity() {
        return Tariff.class;
    }

    @Override
    public void list(ListSpec<Tariff> list) {
        list.title("Тарифы")
                .columns("code", "description", "pricePerPeriod", "periodDays", "available")
                .sortBy("description", false);
    }

    @Override
    public void fields(EntityConfigBuilder<Tariff> f) {
        f.icon("tag");
        f.field("code").label("Код").order(0).width("half")
                .hint("Присваивается автоматически.");
        f.field("description").label("Наименование").order(1).width("half")
                .placeholder("Базовый, месяц").hint("Обязательное поле.");
        f.field(Tariff::getPricePerPeriod).order(2).width("half").format(Formats.MONEY)
                .hint("Цена одного периода. Подставляется в строки подписок.");
        f.field(Tariff::getPeriodDays).order(3).width("half").format("integer")
                .hint("Сколько дней длится один период, например 30 или 365.");
        f.field(Tariff::getAvailable).order(4).widget("switch")
                .hint("Недоступный тариф нельзя добавить в новую подписку.");
    }
}
