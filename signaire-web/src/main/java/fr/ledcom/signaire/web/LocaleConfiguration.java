package fr.ledcom.signaire.web;

import java.util.List;
import java.util.Locale;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Language of the UI: the one chosen with the {@code lang} parameter (remembered in a cookie), else the
 * browser's preferred language, else French. Only languages with translations are used.
 */
@Configuration(proxyBeanMethods = false)
public class LocaleConfiguration implements WebMvcConfigurer {

    /** Languages with a complete messages bundle. Add a locale here when adding messages_xx.properties. */
    static final List<Locale> SUPPORTED_LOCALES = List.of(Locale.FRENCH, Locale.GERMAN, Locale.ITALIAN,
            Locale.ENGLISH);
    static final String COOKIE = "signaire-langue";
    static final String PARAMETER = "lang";

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver browserLanguage = new AcceptHeaderLocaleResolver();
        browserLanguage.setSupportedLocales(SUPPORTED_LOCALES);
        browserLanguage.setDefaultLocale(Locale.FRENCH);
        CookieLocaleResolver resolver = new SupportedLocaleResolver();
        resolver.setDefaultLocaleFunction(browserLanguage::resolveLocale);
        return resolver;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName(PARAMETER);
        interceptor.setIgnoreInvalidLocale(true);
        registry.addInterceptor(interceptor);
    }

    /** Ignores the choice of unsupported languages. */
    private static final class SupportedLocaleResolver extends CookieLocaleResolver {
        SupportedLocaleResolver() {
            super(COOKIE);
        }

        @Override
        public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
            Locale language = locale == null ? null : Locale.of(locale.getLanguage());
            super.setLocale(request, response, SUPPORTED_LOCALES.contains(language) ? language : null);
        }
    }
}
