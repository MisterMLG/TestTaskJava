package dev.subscriptions;

import dev.subscriptions.domain.catalogs.Customer;
import dev.subscriptions.domain.catalogs.Tariff;
import dev.subscriptions.domain.documents.Payment;
import dev.subscriptions.domain.documents.Subscription;
import dev.subscriptions.domain.documents.SubscriptionLine;
import dev.subscriptions.domain.registers.CustomerAccount;
import dev.subscriptions.domain.registers.TariffRevenue;
import dev.subscriptions.repositories.CustomerRepository;
import dev.subscriptions.repositories.PaymentRepository;
import dev.subscriptions.repositories.SubscriptionRepository;
import dev.subscriptions.repositories.TariffRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import su.onno.posting.PostingService;
import su.onno.repository.RegisterRepository;
import su.onno.types.Ref;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
abstract class IntegrationTestBase {
    @Autowired protected CustomerRepository customers;
    @Autowired protected TariffRepository tariffs;
    @Autowired protected PaymentRepository payments;
    @Autowired protected SubscriptionRepository subscriptions;
    @Autowired protected PostingService posting;
    @Autowired protected RegisterRepository<CustomerAccount> accounts;
    @Autowired protected RegisterRepository<TariffRevenue> revenue;

    protected Customer customer() {
        Customer customer = new Customer();
        customer.setDescription("Клиент " + UUID.randomUUID());
        customer.setEmail("client@example.com");
        return customers.save(customer);
    }

    protected Tariff tariff(String price, int periodDays, boolean available) {
        Tariff tariff = new Tariff();
        tariff.setDescription("Тариф " + UUID.randomUUID());
        tariff.setPricePerPeriod(new BigDecimal(price));
        tariff.setPeriodDays(periodDays);
        tariff.setAvailable(available);
        return tariffs.save(tariff);
    }

    protected void pay(Customer customer, String amount) {
        Payment payment = new Payment();
        payment.setCustomer(Ref.of(Customer.class, customer.getId()));
        payment.setAmount(new BigDecimal(amount));
        posting.post(payments.save(payment));
    }

    protected static Subscription subscription(Customer customer, SubscriptionLine... lines) {
        Subscription subscription = new Subscription();
        if (customer != null) {
            subscription.setCustomer(Ref.of(Customer.class, customer.getId()));
        }
        subscription.getLines().addAll(java.util.List.of(lines));
        return subscription;
    }

    protected static SubscriptionLine line(Tariff tariff, int periods) {
        SubscriptionLine line = new SubscriptionLine();
        line.setTariff(Ref.of(Tariff.class, tariff.getId()));
        line.setPeriods(periods);
        return line;
    }

    protected BigDecimal balance(Customer customer) {
        Ref<Customer> ref = Ref.of(Customer.class, customer.getId());
        return accounts.getBalance(f -> f.where(CustomerAccount::getCustomer, ref)).stream()
                .map(CustomerAccount::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    protected TariffRevenue turnover(Customer customer) {
        Ref<Customer> ref = Ref.of(Customer.class, customer.getId());
        var rows = revenue.getTurnover(LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1),
                f -> f.where(TariffRevenue::getCustomer, ref));
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    protected Subscription reload(Subscription subscription) {
        return subscriptions.findById(subscription.getId()).orElseThrow();
    }

    protected Subscription saveAndPost(Subscription draft) {
        Subscription saved = subscriptions.save(draft);
        posting.post(saved);
        return reload(saved);
    }
}
