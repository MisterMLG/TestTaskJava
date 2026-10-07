package dev.subscriptions.ui.views;

import dev.subscriptions.domain.catalogs.Customer;
import dev.subscriptions.domain.documents.Subscription;
import dev.subscriptions.domain.documents.SubscriptionLine;
import dev.subscriptions.domain.enumerations.SubscriptionStatus;
import dev.subscriptions.service.SubscriptionService;
import dev.subscriptions.ui.Formats;
import org.springframework.stereotype.Component;
import su.onno.ui.ActionContext;
import su.onno.ui.ActionRejectedException;
import su.onno.ui.ActionResult;
import su.onno.ui.ActionRow;
import su.onno.ui.ActionScope;
import su.onno.ui.ActionSeverity;
import su.onno.ui.ActionSpec;
import su.onno.ui.ActionToast;
import su.onno.ui.EntityConfigBuilder;
import su.onno.ui.EntityView;
import su.onno.ui.InputType;
import su.onno.ui.ListSpec;

@Component
public class SubscriptionView implements EntityView<Subscription> {
    private final SubscriptionService subscriptions;

    public SubscriptionView(SubscriptionService subscriptions) {
        this.subscriptions = subscriptions;
    }

    @Override
    public Class<Subscription> entity() {
        return Subscription.class;
    }

    @Override
    public void list(ListSpec<Subscription> list) {
        list.title("Подписки")
                .columns("number", "date", "customer", "status", "startDate", "endDate", "total", "posted")
                .sortBy("date", true)
                .groupable(Subscription::getStatus, Subscription::getCustomer)
                .aggregate(Subscription::getTotal, ListSpec.Agg.SUM, "Итог");
        list.filter(Subscription::getStatus).label("Статус").multiOptions();
        list.filter(Subscription::getCustomer).label("Клиент");
        list.filter(Subscription::getEndDate).label("Дата окончания").dateRange();
        list.rowStyle(row -> {
            SubscriptionStatus status = row.enumValue(Subscription::getStatus, SubscriptionStatus.class);
            if (status == SubscriptionStatus.CANCELLED || status == SubscriptionStatus.EXPIRED) {
                return ListSpec.RowStyle.MUTED;
            }
            return status == SubscriptionStatus.ACTIVE ? ListSpec.RowStyle.SUCCESS : null;
        });
    }

    @Override
    public void fields(EntityConfigBuilder<Subscription> f) {
        f.icon("repeat");
        f.field("number").label("Номер").order(0).width("half")
                .hint("Присваивается автоматически.");
        f.field("date").label("Дата документа").order(1).width("half").format(Formats.DATE_TIME)
                .hint("По умолчанию текущий момент.");
        f.field("posted").label("Проведена");
        f.refField(Subscription::getCustomer).order(2).width("half")
                .refSecondary(Customer::getEmail).placeholder("Выберите клиента")
                .hint("С чьего лицевого счета списывается стоимость. Обязательное поле.");
        f.field(Subscription::getStatus).order(3).width("half")
                .hint("Меняется при проведении, отмене и регламентным заданием по датам.");
        f.field(Subscription::getStartDate).order(4).width("half").format(Formats.DATE)
                .hint("По умолчанию сегодняшняя дата.");
        f.field(Subscription::getEndDate).order(5).width("half").format(Formats.DATE).group("Расчет")
                .hint("Дата начала плюс самый длинный срок среди строк. Пересчитывается при каждом сохранении.");
        f.field(Subscription::getTotal).order(6).width("half").format(Formats.MONEY).group("Расчет")
                .hint("Сумма строк. Пересчитывается при каждом сохранении, списывается при проведении.");
        f.field(Subscription::getCancelReason).order(7).widget("textarea").hideInList().group("Отмена")
                .hint("Заполняется действием \"Отменить подписку\".");

        f.rowRefField(Subscription::getLines, SubscriptionLine::getTariff).label("Тариф").placeholder("Выберите тариф")
                .refFilter("available = true");
        f.rowField(Subscription::getLines, SubscriptionLine::getPeriods).label("Периодов").format("integer");
        f.rowField(Subscription::getLines, SubscriptionLine::getPrice).label("Цена").format(Formats.MONEY)
                .hint("Подставляется из тарифа при сохранении.");
        f.rowField(Subscription::getLines, SubscriptionLine::getAmount).label("Сумма").format(Formats.MONEY)
                .hint("Цена, умноженная на число периодов.");

        f.action("cancelTop").inMenu();
    }

    @Override
    public void actions(ActionSpec a) {
        a.action("cancel").scope(ActionScope.ROW).icon("ban").label("Отменить")
                .visibleWhen(SubscriptionView::canCancel)
                .form(SubscriptionView::cancelForm)
                .handler(this::cancel);
        a.action("cancelTop").scope(ActionScope.DETAIL).icon("ban").label("Отменить подписку")
                .visibleWhen(SubscriptionView::canCancel)
                .form(SubscriptionView::cancelForm)
                .handler(this::cancel);
    }

    private static boolean canCancel(ActionRow row) {
        return row.enumValue(Subscription::getStatus, SubscriptionStatus.class) != SubscriptionStatus.CANCELLED;
    }

    private static void cancelForm(su.onno.ui.InputSpec f) {
        f.title("Отмена подписки")
                .description("Если подписка проведена, списанная сумма вернется на лицевой счет, "
                        + "а выручка будет снята.")
                .submitLabel("Отменить подписку")
                .cancelLabel("Назад")
                .tone(ActionSeverity.WARNING);
        f.input("reason").label("Причина отмены").type(InputType.TEXTAREA)
                .placeholder("Например: клиент отказался от услуги").required();
    }

    private ActionResult cancel(ActionContext ctx) {
        try {
            subscriptions.cancel(ctx.id(), ctx.input("reason"));
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw ActionRejectedException.message(e.getMessage());
        }
        return ActionResult.refresh(ActionToast.success("Подписка отменена"));
    }
}
