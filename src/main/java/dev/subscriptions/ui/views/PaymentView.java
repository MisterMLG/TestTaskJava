package dev.subscriptions.ui.views;

import dev.subscriptions.domain.catalogs.Customer;
import dev.subscriptions.domain.documents.Payment;
import dev.subscriptions.ui.Formats;
import org.springframework.stereotype.Component;
import su.onno.ui.EntityConfigBuilder;
import su.onno.ui.EntityView;
import su.onno.ui.ListSpec;

@Component
public class PaymentView implements EntityView<Payment> {
    @Override
    public Class<Payment> entity() {
        return Payment.class;
    }

    @Override
    public void list(ListSpec<Payment> list) {
        list.title("Платежи")
                .columns("number", "date", "customer", "amount", "paymentMethod", "posted")
                .sortBy("date", true)
                .groupable(Payment::getCustomer, Payment::getPaymentMethod)
                .aggregate(Payment::getAmount, ListSpec.Agg.SUM, "Сумма");
        list.filter(Payment::getCustomer).label("Клиент");
        list.filter(Payment::getPaymentMethod).label("Способ оплаты").multiOptions();
        list.filter("date").label("Дата").dateRange();
    }

    @Override
    public void fields(EntityConfigBuilder<Payment> f) {
        f.icon("banknote");
        f.field("number").label("Номер").order(0).width("half")
                .hint("Присваивается автоматически.");
        f.field("date").label("Дата").order(1).width("half").format(Formats.DATE_TIME)
                .hint("По умолчанию текущий момент.");
        f.field("posted").label("Проведен");
        f.refField(Payment::getCustomer).order(2).width("half")
                .refSecondary(Customer::getEmail).placeholder("Выберите клиента")
                .hint("Чей лицевой счет пополняется. Обязательное поле.");
        f.field(Payment::getPaymentMethod).order(3).width("half");
        f.field(Payment::getAmount).order(4).width("half").format(Formats.MONEY)
                .hint("Зачисляется на лицевой счет при проведении. Должна быть больше нуля.");
    }
}
