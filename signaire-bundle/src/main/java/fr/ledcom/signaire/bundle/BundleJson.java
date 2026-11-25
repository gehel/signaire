package fr.ledcom.signaire.bundle;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;

import tools.jackson.databind.json.JsonMapper;

/** JSON mapping shared by the bundle reader and writer. */
final class BundleJson {

    /**
     * Records written from their fields: on Android, {@code d8} makes records classes without record components, whose
     * accessors Jackson doesn't see; it would write them as {@code {}}.
     */
    static final JsonMapper MAPPER = JsonMapper.builder()
            .changeDefaultVisibility(visibility -> visibility.withVisibility(PropertyAccessor.FIELD, Visibility.ANY))
            .build();

    private BundleJson() {
    }
}
