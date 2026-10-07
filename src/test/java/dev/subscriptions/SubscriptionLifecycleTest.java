package dev.subscriptions;

import dev.subscriptions.domain.catalogs.Customer;
import dev.subscriptions.domain.catalogs.Tariff;
import dev.subscriptions.domain.documents.Payment;
import dev.subscriptions.domain.documents.Subscription;
import dev.subscriptions.domain.enumerations.SubscriptionStatus;
import dev.subscriptions.jobs.SubscriptionStatusJob;
import dev.subscriptions.service.SubscriptionService;
import org.jobrunr.storage.StorageProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import su.onno.types.Ref;
import su.onno.validation.ValidationException;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubscriptionLifecycleTest extends IntegrationTestBase {
    @Autowired SubscriptionService service;
    @Autowired SubscriptionStatusJob job;
    @Autowired StorageProvider jobStorage;

    @Test
    @Tag("BR-14")
    @DisplayName("BR-14: дата документа и дата начала подставляются при создании")
    void datesAreFilledOnCreate() {
        Subscription saved = reload(subscriptions.save(subscription(customer(), line(tariff("100.00", 30, true), 1))));

        assertThat(saved.getDate()).isNotNull();
        assertThat(saved.getDate().toLocalDate()).isEqualTo(LocalDate.now());
        assertThat(saved.getStartDate()).isEqualTo(LocalDate.now());
        assertThat(saved.getStatus()).isEqualTo(SubscriptionStatus.DRAFT);

        Payment payment = new Payment();
        payment.setCustomer(Ref.of(Customer.class, customer().getId()));
        payment.setAmount(new BigDecimal("10.00"));
        assertThat(payments.save(payment).getDate()).isNotNull();
    }

    @Test
    @Tag("BR-15")
    @DisplayName("BR-15: платеж без клиента или с неположительной суммой не сохраняется")
    void paymentRequiresCustomerAndPositiveAmount() {
        Payment withoutCustomer = new Payment();
        withoutCustomer.setAmount(new BigDecimal("10.00"));
        assertThatThrownBy(() -> payments.save(withoutCustomer))
                .isInstanceOfSatisfying(ValidationException.class, e ->
                        assertThat(e.fieldErrors().get("customer")).contains("Укажите клиента"));

        Payment zero = new Payment();
        zero.setCustomer(Ref.of(Customer.class, customer().getId()));
        zero.setAmount(BigDecimal.ZERO);
        assertThatThrownBy(() -> payments.save(zero))
                .isInstanceOfSatisfying(ValidationException.class, e ->
                        assertThat(e.fieldErrors().get("amount"))
                                .contains("Сумма платежа должна быть больше нуля"));
    }

    @Test
    @Tag("BR-11")
    @DisplayName("BR-11: при проведении подписка с сегодняшней датой начала становится активной")
    void postingActivatesSubscriptionStartingToday() {
        Customer customer = customer();
        pay(customer, "1000.00");

        Subscription posted = saveAndPost(subscription(customer, line(tariff("100.00", 30, true), 1)));

        assertThat(posted.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    @Tag("BR-11")
    @DisplayName("BR-11: актуализация статусов переводит подписку в PENDING, ACTIVE и EXPIRED по датам")
    void refreshMovesStatusesByDates() {
        Customer customer = customer();
        pay(customer, "1000.00");
        Subscription draft = subscription(customer, line(tariff("100.00", 30, true), 1));
        LocalDate start = LocalDate.now().plusDays(10);
        draft.setStartDate(start);
        Subscription posted = saveAndPost(draft);
        assertThat(posted.getEndDate()).isEqualTo(start.plusDays(30));

        service.refreshStatuses(start.minusDays(1));
        assertThat(reload(posted).getStatus()).isEqualTo(SubscriptionStatus.PENDING);

        service.refreshStatuses(start);
        assertThat(reload(posted).getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);

        service.refreshStatuses(start.plusDays(29));
        assertThat(reload(posted).getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);

        service.refreshStatuses(start.plusDays(30));
        assertThat(reload(posted).getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(balance(customer)).isEqualByComparingTo("900");
    }

    @Test
    @Tag("BR-11")
    @DisplayName("BR-11: актуализация не трогает черновики и отмененные подписки")
    void refreshSkipsDraftsAndCancelled() {
        Customer customer = customer();
        pay(customer, "1000.00");
        Tariff monthly = tariff("100.00", 30, true);
        Subscription draft = subscriptions.save(subscription(customer, line(monthly, 1)));
        Subscription cancelled = saveAndPost(subscription(customer, line(monthly, 1)));
        service.cancel(cancelled.getId(), "Клиент передумал");

        service.refreshStatuses(LocalDate.now().plusYears(5));

        assertThat(reload(draft).getStatus()).isEqualTo(SubscriptionStatus.DRAFT);
        assertThat(reload(cancelled).getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
    }

    @Test
    @Tag("BR-11")
    @DisplayName("BR-11: регламентное задание зарегистрировано в планировщике и выполняется без ошибок")
    void jobIsScheduledAndRuns() {
        assertThat(jobStorage.getRecurringJobs())
                .extracting(recurring -> recurring.getId())
                .contains("SubscriptionStatusRefresh");
        assertThatCode(job::execute).doesNotThrowAnyException();
    }

    @Test
    @Tag("BR-06")
    @Tag("BR-12")
    @DisplayName("BR-12: отмена проведенной подписки возвращает деньги и снимает выручку")
    void cancellingPostedSubscriptionRefunds() {
        Customer customer = customer();
        pay(customer, "1000.00");
        Subscription posted = saveAndPost(subscription(customer, line(tariff("100.00", 30, true), 3)));
        assertThat(balance(customer)).isEqualByComparingTo("700");

        service.cancel(posted.getId(), "  Клиент отказался от услуги  ");

        Subscription cancelled = reload(posted);
        assertThat(cancelled.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(cancelled.getCancelReason()).isEqualTo("Клиент отказался от услуги");
        assertThat(cancelled.isPosted()).isFalse();
        assertThat(balance(customer)).isEqualByComparingTo("1000");
        assertThat(revenue.getRecordsByDocument(posted.getId())).noneMatch(r -> r.isActive());
        assertThat(accounts.getRecordsByDocument(posted.getId())).noneMatch(r -> r.isActive());
    }

    @Test
    @Tag("BR-12")
    @DisplayName("BR-12: повторная отмена и отмена без причины отклоняются")
    void cancelIsRejectedTwiceOrWithoutReason() {
        Customer customer = customer();
        Subscription draft = subscriptions.save(subscription(customer, line(tariff("100.00", 30, true), 1)));

        assertThatThrownBy(() -> service.cancel(draft.getId(), " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Укажите причину отмены");

        service.cancel(draft.getId(), "Ошибка оператора");
        assertThatThrownBy(() -> service.cancel(draft.getId(), "Еще раз"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("уже отменена");
    }

    @Test
    @Tag("BR-09")
    @DisplayName("BR-09: у черновика цена следует за тарифом, у проведенной подписки зафиксирована")
    void priceFollowsTariffOnlyBeforePosting() {
        Customer customer = customer();
        pay(customer, "1000.00");
        Tariff tariff = tariff("100.00", 30, true);
        Subscription draft = subscriptions.save(subscription(customer, line(tariff, 1)));
        Subscription posted = saveAndPost(subscription(customer, line(tariff, 1)));

        tariff.setPricePerPeriod(new BigDecimal("250.00"));
        tariffs.save(tariff);

        Subscription draftResaved = reload(subscriptions.save(reload(draft)));
        Subscription postedResaved = reload(subscriptions.save(reload(posted)));
        assertThat(draftResaved.getTotal()).isEqualByComparingTo("250");
        assertThat(postedResaved.getTotal()).isEqualByComparingTo("100");
    }

    @Test
    @Tag("BR-04")
    @DisplayName("BR-04: проведенная подписка сохраняется и после снятия тарифа с продажи")
    void postedSubscriptionSurvivesTariffWithdrawal() {
        Customer customer = customer();
        pay(customer, "1000.00");
        Tariff tariff = tariff("100.00", 30, true);
        Subscription posted = saveAndPost(subscription(customer, line(tariff, 1)));

        tariff.setAvailable(false);
        tariffs.save(tariff);

        assertThatCode(() -> subscriptions.save(reload(posted))).doesNotThrowAnyException();
        assertThatThrownBy(() -> subscriptions.save(subscription(customer, line(tariff, 1))))
                .isInstanceOf(ValidationException.class)
                .hasStackTraceContaining("Тариф недоступен для подключения");
    }

    @Test
    @Tag("BR-05")
    @Tag("BR-13")
    @DisplayName("BR-05: перепроведение учитывает собственное прежнее списание")
    void repostCountsOwnPreviousExpense() {
        Customer customer = customer();
        pay(customer, "500.00");
        Subscription posted = saveAndPost(subscription(customer, line(tariff("100.00", 30, true), 3)));
        assertThat(balance(customer)).isEqualByComparingTo("200");

        posted.getLines().get(0).setPeriods(5);
        Subscription edited = reload(subscriptions.save(posted));
        posting.repost(edited);
        assertThat(balance(customer)).isEqualByComparingTo("0");
        assertThat(turnover(customer).getAmount()).isEqualByComparingTo("500");

        Subscription again = reload(edited);
        again.getLines().get(0).setPeriods(6);
        Subscription tooExpensive = reload(subscriptions.save(again));
        assertThatThrownBy(() -> posting.repost(tooExpensive))
                .hasStackTraceContaining("Недостаточно средств на лицевом счете");
        assertThat(balance(customer)).isEqualByComparingTo("0");
    }
}
