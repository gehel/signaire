package fr.ledcom.signaire.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import fr.ledcom.signaire.accounts.AccountService;
import fr.ledcom.signaire.accounts.SignOverrideService;
import fr.ledcom.signaire.accounts.SignRequestService;
import fr.ledcom.signaire.accounts.WordListService;
import fr.ledcom.signaire.core.SignaireCoreConfiguration;

/**
 * Controllers with the security of the application and the core services (a Lucene index under {@code target/}),
 * without database: the services of the accounts are mocks, which tests autowire to stub and verify them. Tests of
 * the same controllers share their Spring context.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@WebMvcTest
@Import({SecurityConfiguration.class, AdminUsers.class, SignaireCoreConfiguration.class, TestUserAgent.class})
@MockitoBean(types = {AccountService.class, WordListService.class, SignOverrideService.class,
    SignRequestService.class})
public @interface WebLayerTest {

    /** The controllers to test. */
    @AliasFor(annotation = WebMvcTest.class, attribute = "controllers")
    Class<?>[] value() default {};
}
