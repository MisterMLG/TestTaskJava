package dev.subscriptions;

import dev.subscriptions.domain.catalogs.Customer;
import dev.subscriptions.domain.catalogs.Tariff;
import dev.subscriptions.domain.documents.Subscription;
import dev.subscriptions.domain.documents.SubscriptionLine;
import dev.subscriptions.domain.enumerations.SubscriptionStatus;
import dev.subscriptions.domain.registers.TariffRevenue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import su.onno.validation.ValidationException;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubscriptionFlowTest extends IntegrationTestBase {
    @Test
    @Tag("BR-10")
    @DisplayName("BR-10: проведенный платеж увеличивает остаток лицевого счета на свою сумму")
    void paymentIncreasesBalance() {
        Customer customer = customer();

        pay(customer, "1000.00");
        pay(customer, "250.50");

        assertThat(balance(customer)).isEqualByComparingTo("1250.50");
    }

    @Test
    @Tag("BR-07")
    @Tag("BR-08")
    @Tag("BR-09")
    @DisplayName("BR-07, BR-08, BR-09: цена из тарифа, суммы строк, итог и дата окончания считаются при сохранении")
    void savingFillsPriceAmountsTotalAndEndDate() {
        Customer customer = customer();
        Tariff monthly = tariff("100.00", 30, true);
        Tariff yearly = tariff("1000.00", 365, true);
        Subscription draft = subscription(customer, line(monthly, 3), line(yearly, 1));
        draft.setStartDate(LocalDate.of(2026, 1, 1));

        Subscription saved = subscriptions.findById(subscriptions.save(draft).getId()).orElseThrow();

        assertThat(saved.getLines()).extracting(SubscriptionLine::getPrice)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("100"), new BigDecimal("1000"));
        assertThat(saved.getLines()).extracting(SubscriptionLine::getAmount)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("300"), new BigDecimal("1000"));
        assertThat(saved.getTotal()).isEqualByComparingTo("1300");
        assertThat(saved.getEndDate()).isEqualTo(LocalDate.of(2027, 1, 1));
    }

    @Test
    @Tag("BR-07")
    @Tag("BR-08")
    @DisplayName("BR-07, BR-08: повторное сохранение пересчитывает суммы и дату окончания")
    void resavingRecalculates() {
        Customer customer = customer();
        Tariff monthly = tariff("100.00", 30, true);
        Subscription draft = subscription(customer, line(monthly, 1));
        draft.setStartDate(LocalDate.of(2026, 1, 1));
        Subscription saved = subscriptions.findById(subscriptions.save(draft).getId()).orElseThrow();

        saved.getLines().get(0).setPeriods(4);
        Subscription resaved = subscriptions.findById(subscriptions.save(saved).getId()).orElseThrow();

        assertThat(resaved.getTotal()).isEqualByComparingTo("400");
        assertThat(resaved.getEndDate()).isEqualTo(LocalDate.of(2026, 5, 1));
    }

    @Test
    @Tag("BR-05")
    @Tag("BR-13")
    @DisplayName("BR-13: проведение подписки списывает итог со счета и признает выручку по строкам")
    void postingSubscriptionWritesOffAndRecognizesRevenue() {
        Customer customer = customer();
        Tariff monthly = tariff("100.00", 30, true);
        pay(customer, "1000.00");

        Subscription saved = subscriptions.save(subscription(customer, line(monthly, 3)));
        posting.post(saved);

        assertThat(balance(customer)).isEqualByComparingTo("700");
        TariffRevenue turnover = turnover(customer);
        assertThat(turnover.getAmount()).isEqualByComparingTo("300");
        assertThat(turnover.getPeriods()).isEqualByComparingTo("3");
        assertThat(subscriptions.findById(saved.getId()).orElseThrow().isPosted()).isTrue();
    }

    @Test
    @Tag("BR-05")
    @DisplayName("BR-05: подписка при нехватке денег не проводится, остаток и выручка не меняются")
    void insufficientFundsRejectsPosting() {
        Customer customer = customer();
        Tariff monthly = tariff("100.00", 30, true);
        pay(customer, "700.00");

        Subscription saved = subscriptions.save(subscription(customer, line(monthly, 8)));

        assertThatThrownBy(() -> posting.post(saved))
                .hasStackTraceContaining("Недостаточно средств на лицевом счете");
        assertThat(balance(customer)).isEqualByComparingTo("700");
        assertThat(revenue.getRecordsByDocument(saved.getId())).isEmpty();
        assertThat(subscriptions.findById(saved.getId()).orElseThrow().isPosted()).isFalse();
    }

    @Test
    @Tag("BR-06")
    @DisplayName("BR-06: отмененная подписка не создает движений")
    void cancelledSubscriptionMakesNoMovements() {
        Customer customer = customer();
        Tariff monthly = tariff("100.00", 30, true);
        pay(customer, "1000.00");
        Subscription draft = subscription(customer, line(monthly, 3));
        draft.setStatus(SubscriptionStatus.CANCELLED);

        Subscription saved = subscriptions.save(draft);
        posting.post(saved);

        assertThat(accounts.getRecordsByDocument(saved.getId())).isEmpty();
        assertThat(revenue.getRecordsByDocument(saved.getId())).isEmpty();
        assertThat(balance(customer)).isEqualByComparingTo("1000");
    }

    @Test
    @Tag("BR-01")
    @DisplayName("BR-01: подписка без клиента не сохраняется")
    void customerIsRequired() {
        Subscription draft = subscription(null, line(tariff("100.00", 30, true), 1));

        assertThatThrownBy(() -> subscriptions.save(draft))
                .isInstanceOfSatisfying(ValidationException.class, e ->
                        assertThat(e.fieldErrors().get("customer")).contains("Укажите клиента"));
    }

    @Test
    @Tag("BR-02")
    @DisplayName("BR-02: подписка без строк не сохраняется")
    void atLeastOneLineIsRequired() {
        Subscription draft = subscription(customer());

        assertThatThrownBy(() -> subscriptions.save(draft))
                .isInstanceOf(ValidationException.class)
                .hasStackTraceContaining("Добавьте хотя бы одну строку");
    }

    @Test
    @Tag("BR-03")
    @DisplayName("BR-03: число периодов в строке должно быть больше нуля")
    void periodsMustBePositive() {
        Subscription draft = subscription(customer(), line(tariff("100.00", 30, true), 0));

        assertThatThrownBy(() -> subscriptions.save(draft))
                .isInstanceOf(ValidationException.class)
                .hasStackTraceContaining("Число периодов");
    }

    @Test
    @Tag("BR-04")
    @DisplayName("BR-04: тариф, недоступный для подключения, нельзя добавить в подписку")
    void tariffMustBeAvailable() {
        Subscription draft = subscription(customer(), line(tariff("100.00", 30, false), 1));

        assertThatThrownBy(() -> subscriptions.save(draft))
                .isInstanceOf(ValidationException.class)
                .hasStackTraceContaining("Тариф недоступен для подключения");
    }
}
