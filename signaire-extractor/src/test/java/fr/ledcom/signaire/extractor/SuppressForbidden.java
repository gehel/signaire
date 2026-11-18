package fr.ledcom.signaire.extractor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Suppresses forbiddenapis checks; the annotation is recognized by its simple name. */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
@interface SuppressForbidden {
    /** Why the forbidden API is fine here. */
    String reason();
}
