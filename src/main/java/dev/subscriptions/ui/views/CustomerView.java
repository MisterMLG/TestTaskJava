package dev.subscriptions.ui.views;

import dev.subscriptions.domain.catalogs.Customer;
import dev.subscriptions.ui.Formats;
import org.springframework.stereotype.Component;
import su.onno.ui.EntityConfigBuilder;
import su.onno.ui.EntityView;
import su.onno.ui.ListSpec;

@Component
public class CustomerView implements EntityView<Customer> {
    @Override
    public Class<Customer> entity() {
        return Customer.class;
    }

    @Override
    public void list(ListSpec<Customer> list) {
        list.title("Клиенты")
                .columns("code", "description", "status", "email", "phone", "registrationDate")
                .sortBy("description", false);
        list.filter(Customer::getStatus).label("Статус").multiOptions();
        list.filter(Customer::getRegistrationDate).label("Дата регистрации").dateRange();
    }

    @Override
    public void fields(EntityConfigBuilder<Customer> f) {
        f.icon("users");
        f.field("code").label("Код").order(0).width("half")
                .hint("Присваивается автоматически.");
        f.field("description").label("Имя клиента").order(1).width("half")
                .placeholder("Иванов Иван Иванович").hint("Обязательное поле.");
        f.field(Customer::getStatus).order(2).width("half")
                .hint("Новый клиент создается активным.");
        f.field(Customer::getRegistrationDate).order(3).width("half").format(Formats.DATE)
                .hint("По умолчанию сегодняшняя дата.");
        f.field(Customer::getEmail).order(4).width("half").group("Контакты")
                .placeholder("client@example.com");
        f.field(Customer::getPhone).order(5).width("half").group("Контакты")
                .placeholder("+7 900 000-00-00");
    }
}
