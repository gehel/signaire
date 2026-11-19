package fr.ledcom.signaire.web;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.filter.OncePerRequestFilter;

import fr.ledcom.signaire.accounts.Account;
import fr.ledcom.signaire.accounts.AccountService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * The standalone application used alone: every request from this computer is the single user, an administrator,
 * without logging in. Their account is an ordinary one, with an identity of Signaire rather than of an OpenID
 * Connect provider. Requests from other computers stay anonymous, whatever the address the server listens to.
 */
class SingleUserFilter extends OncePerRequestFilter {

    /** The issuer of the identity of the single user. */
    static final String ISSUER = "urn:signaire:single-user";
    private static final String SUBJECT = "owner";
    private static final String USERNAME = "signaire";
    private static final List<GrantedAuthority> AUTHORITIES = List.of(new SimpleGrantedAuthority("ROLE_USER"),
            new SimpleGrantedAuthority("ROLE_" + AdminUsers.ADMIN_ROLE));

    private final Standalone standalone;
    private final AccountService accounts;
    private final SecurityContextHolderStrategy contexts = SecurityContextHolder.getContextHolderStrategy();
    private final AtomicReference<Account> owner = new AtomicReference<>();

    SingleUserFilter(Standalone standalone, AccountService accounts) {
        this.standalone = standalone;
        this.accounts = accounts;
    }

    /** The single user. */
    record OwnerUser(long accountId, String displayName) implements AccountPrincipal {
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (standalone.singleUser() && isLocal(request) && !loggedIn()) {
            Account account = owner();
            SecurityContext context = contexts.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                    new OwnerUser(account.id(), account.displayName()), null, AUTHORITIES));
            contexts.setContext(context);
        }
        chain.doFilter(request, response);
    }

    private boolean loggedIn() {
        Authentication authentication = contexts.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private Account owner() {
        Account account = owner.get();
        if (account == null) {
            // found or created again by concurrent first requests: the same account
            account = accounts.findOrCreateOidc(ISSUER, SUBJECT, USERNAME, "Signaire");
            owner.set(account);
        }
        return account;
    }

    /** Whether a request comes from this computer. */
    static boolean isLocal(HttpServletRequest request) {
        try {
            // an IP address: no lookup
            return InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress();
        } catch (UnknownHostException _) {
            return false;
        }
    }
}
