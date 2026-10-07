package dev.subscriptions.support;

import dev.subscriptions.domain.catalogs.Tariff;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;
import su.onno.types.Ref;
import su.onno.types.RefResolver;

import java.util.Optional;

@Component
public class DomainLookup implements ApplicationContextAware {
    private static ApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        context = applicationContext;
    }

    public static Optional<Tariff> tariff(Ref<Tariff> ref) {
        if (ref == null || context == null) {
            return Optional.empty();
        }
        return context.getBean(RefResolver.class).resolve(ref);
    }
}
