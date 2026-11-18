package fr.ledcom.signaire.bundle;

import tools.jackson.databind.json.JsonMapper;

/** JSON mapping shared by the bundle reader and writer. */
final class BundleJson {

    static final JsonMapper MAPPER = JsonMapper.builder().build();

    private BundleJson() {
    }
}
