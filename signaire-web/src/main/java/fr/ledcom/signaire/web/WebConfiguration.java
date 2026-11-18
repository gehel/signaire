package fr.ledcom.signaire.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import fr.ledcom.signaire.model.Language;

/** Conversions of request parameters. */
@Configuration(proxyBeanMethods = false)
public class WebConfiguration implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        // sign languages are given by their codes: ?language=fr
        registry.addConverter(String.class, Language.class, Language::fromCode);
    }
}
