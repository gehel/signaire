package fr.ledcom.signaire.web;

import org.springframework.http.HttpHeaders;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** The headers of htmx requests. */
final class Htmx {

    static final String REQUEST = "HX-Request";
    private static final String REQUEST_TYPE = "HX-Request-Type";
    private static final String HISTORY_RESTORE_REQUEST = "HX-History-Restore-Request";

    private Htmx() {
    }

    /**
     * Whether the request asks for a part of a page: htmx requests do, except when htmx restores the history
     * or swaps the whole body.
     */
    static boolean isPartialRequest(HttpServletRequest request, HttpServletResponse response) {
        // the same URL gives a whole page or a part of it: caches must tell them apart
        response.addHeader(HttpHeaders.VARY, REQUEST);
        return "true".equals(request.getHeader(REQUEST))
                && !"true".equals(request.getHeader(HISTORY_RESTORE_REQUEST))
                && !"full".equals(request.getHeader(REQUEST_TYPE));
    }
}
