package dev.subscriptions.ui.pages;

import dev.subscriptions.domain.documents.Payment;
import dev.subscriptions.domain.documents.Subscription;
import org.springframework.stereotype.Component;
import su.onno.ui.ChartBuilder;
import su.onno.ui.Page;
import su.onno.ui.PageBuilder;

@Component
public class DashboardPage implements Page {
    @Override
    public String route() {
        return "/";
    }

    @Override
    public void compose(PageBuilder b) {
        b.title("Дашборд");
        b.subtitle("Платежи, подписки и выручка за период");

        b.widget("Период").type("timeRange").width("full").order(-10)
                .config("presets", "7d,30d,90d,1y,all")
                .config("default", "30d");

        b.widget("Поступило платежей").type("stat").width("1/3").order(0).document(Payment.class)
                .dateField("date")
                .config("metric", "sum").metricField(Payment::getAmount)
                .config("currency", "RUB")
                .config("trend", "false").config("comparison", "true")
                .config("filter", "_posted = true")
                .hint("Сумма проведенных платежей за период.");

        b.widget("Выручка по подпискам").type("stat").width("1/3").order(1).document(Subscription.class)
                .dateField("date")
                .config("metric", "sum").metricField(Subscription::getTotal)
                .config("currency", "RUB")
                .config("trend", "false").config("comparison", "true")
                .config("filter", "_posted = true")
                .hint("Итог проведенных подписок за период. Отмененные не учитываются.");

        b.widget("Оформлено подписок").type("stat").width("1/3").order(2).document(Subscription.class)
                .dateField("date")
                .config("metric", "count")
                .config("trend", "false").config("comparison", "true")
                .config("filter", "_posted = true")
                .hint("Число проведенных подписок за период.");

        b.chart("Выручка по клиентам", Subscription.class).width("1/2").order(10)
                .category(Subscription::getCustomer)
                .sum(Subscription::getTotal).bar().label("Выручка").currency("RUB")
                .filter("_posted = true")
                .hint("Итог проведенных подписок в разрезе клиентов.");

        b.widget("Подписки по статусам").type("chart").width("1/2").order(11).document(Subscription.class)
                .config("kind", "pie").config("groupBy", "statusDisplay")
                .config("metric", "count")
                .hint("Сколько подписок в каждом статусе.");

        b.chart("Выручка по дням", Subscription.class).width("full").order(12)
                .time(Subscription::getDate, ChartBuilder.TimeBucket.DAY)
                .sum(Subscription::getTotal).area().label("Выручка").currency("RUB")
                .filter("_posted = true")
                .hint("Итог проведенных подписок по дате документа.");

        b.widget("Последние подписки").type("list").width("1/2").order(20)
                .document(Subscription.class).maxItems(8)
                .config("titleTemplate", "{number} · {customerDisplay}")
                .config("secondaryField", "statusDisplay");

        b.widget("Последние платежи").type("list").width("1/2").order(21)
                .document(Payment.class).maxItems(8)
                .config("titleTemplate", "{number} · {customerDisplay}")
                .config("secondaryField", "paymentMethodDisplay");
    }
}
