package dev.subscriptions.domain.documents;

import dev.subscriptions.domain.catalogs.Customer;
import dev.subscriptions.domain.catalogs.Tariff;
import dev.subscriptions.domain.enumerations.SubscriptionStatus;
import dev.subscriptions.domain.registers.CustomerAccount;
import dev.subscriptions.domain.registers.TariffRevenue;
import dev.subscriptions.support.DomainLookup;
import lombok.Getter;
import lombok.Setter;
import su.onno.annotations.AccessControl;
import su.onno.annotations.Attribute;
import su.onno.annotations.Document;
import su.onno.annotations.TabularSection;
import su.onno.lifecycle.BeforePostHandler;
import su.onno.lifecycle.BeforeWriteHandler;
import su.onno.lifecycle.OnFillingHandler;
import su.onno.lifecycle.Postable;
import su.onno.model.DocumentObject;
import su.onno.model.MovementType;
import su.onno.posting.PostingContext;
import su.onno.repository.RegisterRepository;
import su.onno.rules.BusinessRule;
import su.onno.rules.Validated;
import su.onno.types.Ref;
import su.onno.validation.ValidationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(name = "Subscriptions", title = "Подписки", numberPrefix = "S-", context = "Subscriptions")
@AccessControl(readRoles = {"MANAGER"}, writeRoles = {"MANAGER"})
@Getter
@Setter
public class Subscription extends DocumentObject
        implements OnFillingHandler, BeforeWriteHandler, BeforePostHandler, Validated, Postable {
    @Attribute(displayName = "Клиент")
    private Ref<Customer> customer;

    @Attribute(displayName = "Статус")
    private SubscriptionStatus status = SubscriptionStatus.DRAFT;

    @Attribute(displayName = "Дата начала")
    private LocalDate startDate = LocalDate.now();

    @Attribute(displayName = "Дата окончания")
    private LocalDate endDate;

    @Attribute(displayName = "Итог", precision = 15, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Attribute(displayName = "Причина отмены", length = 500)
    private String cancelReason;

    @TabularSection(name = "lines")
    private List<SubscriptionLine> lines = new ArrayList<>();

    @Override
    public void onFilling() {
        if (getDate() == null) {
            setDate(LocalDateTime.now());
        }
        if (startDate == null) {
            startDate = getDate().toLocalDate();
        }
        if (status == null) {
            status = SubscriptionStatus.DRAFT;
        }
    }

    @Override
    public void beforeWrite() {
        if (startDate == null && getDate() != null) {
            startDate = getDate().toLocalDate();
        }
        if (status == null) {
            status = SubscriptionStatus.DRAFT;
        }
        BigDecimal sum = BigDecimal.ZERO;
        long maxDays = 0;
        for (SubscriptionLine line : safeLines()) {
            Tariff tariff = DomainLookup.tariff(line.getTariff()).orElse(null);
            if (tariff != null && !isPosted()) {
                line.setPrice(tariff.getPricePerPeriod());
            }
            int periods = line.getPeriods() == null ? 0 : line.getPeriods();
            BigDecimal price = line.getPrice() == null ? BigDecimal.ZERO : line.getPrice();
            line.setAmount(price.multiply(BigDecimal.valueOf(periods)));
            sum = sum.add(line.getAmount());
            if (tariff != null && tariff.getPeriodDays() != null) {
                maxDays = Math.max(maxDays, (long) periods * tariff.getPeriodDays());
            }
        }
        total = sum;
        endDate = startDate == null ? null : startDate.plusDays(maxDays);
    }

    @Override
    public void beforePost() {
        if (status != SubscriptionStatus.CANCELLED) {
            status = statusWhenPosted(LocalDate.now());
        }
    }

    public SubscriptionStatus expectedStatus(LocalDate today) {
        if (status == SubscriptionStatus.CANCELLED) {
            return SubscriptionStatus.CANCELLED;
        }
        return isPosted() ? statusWhenPosted(today) : SubscriptionStatus.DRAFT;
    }

    private SubscriptionStatus statusWhenPosted(LocalDate today) {
        if (endDate != null && !today.isBefore(endDate)) {
            return SubscriptionStatus.EXPIRED;
        }
        if (startDate != null && today.isBefore(startDate)) {
            return SubscriptionStatus.PENDING;
        }
        return SubscriptionStatus.ACTIVE;
    }

    @Override
    public List<BusinessRule> rules() {
        return List.of(
                BusinessRule.onField("customer", "Укажите клиента", () -> customer != null),
                new BusinessRule("lines-required", "Добавьте хотя бы одну строку с тарифом",
                        () -> !safeLines().isEmpty()),
                new BusinessRule("line-tariff-required", "В каждой строке должен быть указан тариф",
                        () -> safeLines().stream().allMatch(l -> l.getTariff() != null)),
                new BusinessRule("line-periods-positive", "Число периодов в строке должно быть больше нуля",
                        () -> safeLines().stream()
                                .allMatch(l -> l.getPeriods() != null && l.getPeriods() > 0)),
                new BusinessRule("tariff-available", "Тариф недоступен для подключения",
                        () -> isPosted() || safeLines().stream()
                                .filter(l -> l.getTariff() != null)
                                .allMatch(l -> isAvailable(l.getTariff()))));
    }

    @Override
    public void handlePosting(PostingContext context) {
        if (status == SubscriptionStatus.CANCELLED) {
            return;
        }
        var account = context.movements(CustomerAccount.class);
        requireFunds(account);
        account.addExpense(r -> {
            r.setCustomer(customer);
            r.setAmount(total);
        });
        var revenue = context.movements(TariffRevenue.class);
        for (SubscriptionLine line : safeLines()) {
            revenue.addReceipt(r -> {
                r.setTariff(line.getTariff());
                r.setCustomer(customer);
                r.setAmount(line.getAmount());
                r.setPeriods(BigDecimal.valueOf(line.getPeriods()));
            });
        }
    }

    private void requireFunds(RegisterRepository<CustomerAccount> account) {
        BigDecimal balance = account.getBalance(f -> f.where(CustomerAccount::getCustomer, customer))
                .stream()
                .map(CustomerAccount::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal ownExpense = getId() == null ? BigDecimal.ZERO
                : account.getRecordsByDocument(getId()).stream()
                        .filter(r -> r.isActive() && r.getMovementType() == MovementType.EXPENSE)
                        .map(CustomerAccount::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal available = balance.add(ownExpense);
        if (available.compareTo(total) < 0) {
            throw new ValidationException("Недостаточно средств на лицевом счете: доступно "
                    + available.toPlainString() + ", требуется " + total.toPlainString());
        }
    }

    private static boolean isAvailable(Ref<Tariff> ref) {
        return DomainLookup.tariff(ref)
                .map(t -> !t.isDeletionMark() && Boolean.TRUE.equals(t.getAvailable()))
                .orElse(false);
    }

    private List<SubscriptionLine> safeLines() {
        return lines == null ? List.of() : lines;
    }
}
