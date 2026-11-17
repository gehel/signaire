package fr.ledcom.signaire.web;

import java.util.List;
import java.util.Locale;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/** Restricts the UI to the languages that have translations; French is the default. */
@Configuration(proxyBeanMethods = false)
public class LocaleConfiguration {

    /** Languages with a complete messages bundle. Add a locale here when adding messages_xx.properties. */
    static final List<Locale> SUPPORTED_LOCALES = List.of(Locale.FRENCH);

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setSupportedLocales(SUPPORTED_LOCALES);
        resolver.setDefaultLocale(Locale.FRENCH);
        return resolver;
    }
}
