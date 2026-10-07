package dev.subscriptions.domain.documents;

import dev.subscriptions.domain.catalogs.Customer;
import dev.subscriptions.domain.enumerations.PaymentMethod;
import dev.subscriptions.domain.registers.CustomerAccount;
import lombok.Getter;
import lombok.Setter;
import su.onno.annotations.AccessControl;
import su.onno.annotations.Attribute;
import su.onno.annotations.Document;
import su.onno.lifecycle.OnFillingHandler;
import su.onno.lifecycle.Postable;
import su.onno.model.DocumentObject;
import su.onno.posting.PostingContext;
import su.onno.rules.BusinessRule;
import su.onno.rules.Validated;
import su.onno.types.Ref;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Document(name = "Payments", title = "Платежи", numberPrefix = "P-", context = "Subscriptions")
@AccessControl(readRoles = {"MANAGER"}, writeRoles = {"MANAGER"})
@Getter
@Setter
public class Payment extends DocumentObject implements OnFillingHandler, Validated, Postable {
    @Attribute(displayName = "Клиент")
    private Ref<Customer> customer;

    @Attribute(displayName = "Сумма", precision = 15, scale = 2)
    private BigDecimal amount;

    @Attribute(displayName = "Способ оплаты")
    private PaymentMethod paymentMethod = PaymentMethod.CARD;

    @Override
    public void onFilling() {
        if (getDate() == null) {
            setDate(LocalDateTime.now());
        }
        if (paymentMethod == null) {
            paymentMethod = PaymentMethod.CARD;
        }
    }

    @Override
    public List<BusinessRule> rules() {
        return List.of(
                BusinessRule.onField("customer", "Укажите клиента", () -> customer != null),
                BusinessRule.onField("amount", "Сумма платежа должна быть больше нуля",
                        () -> amount != null && amount.signum() > 0));
    }

    @Override
    public void handlePosting(PostingContext context) {
        context.movements(CustomerAccount.class).addReceipt(r -> {
            r.setCustomer(customer);
            r.setAmount(amount);
        });
    }
}
