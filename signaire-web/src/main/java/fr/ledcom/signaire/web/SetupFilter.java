package fr.ledcom.signaire.web;

import java.io.IOException;
import java.util.List;

import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Sends to the setup the visitors of a standalone application that runs for the first time, whatever the page: in
 * the security filters, before logins are asked.
 */
class SetupFilter extends OncePerRequestFilter {

    /** What the setup page needs, and the manual. */
    private static final List<String> ALWAYS = List.of("/setup", "/help", "/css/", "/webjars/", "/favicon", "/error");

    private final Standalone standalone;

    SetupFilter(Standalone standalone) {
        this.standalone = standalone;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (standalone.needsSetup() && ALWAYS.stream().noneMatch(path::startsWith)) {
            response.sendRedirect(request.getContextPath() + "/setup");
            return;
        }
        chain.doFilter(request, response);
    }
}
