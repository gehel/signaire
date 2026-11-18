package fr.ledcom.signaire.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import fr.ledcom.signaire.accounts.Account;
import fr.ledcom.signaire.accounts.AccountService;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

class SingleUserFilterTest {

    @TempDir
    Path config;

    private final AccountService accounts = mock(AccountService.class);
    private final AtomicReference<Authentication> seen = new AtomicReference<>();

    @AfterEach
    void forget() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void localVisitorsAreTheSingleUserAndAnAdministrator() throws IOException, ServletException {
        when(accounts.findOrCreateOidc(eq(SingleUserFilter.ISSUER), any(), any(), any()))
                .thenReturn(new Account(7, "signaire", "Signaire"));
        SingleUserFilter filter = new SingleUserFilter(standalone(true), accounts);

        filter.doFilter(request("127.0.0.1"), new MockHttpServletResponse(), chain());
        filter.doFilter(request("::1"), new MockHttpServletResponse(), chain());

        assertThat(seen.get().getPrincipal()).isInstanceOfSatisfying(AccountPrincipal.class,
                user -> assertThat(user.accountId()).isEqualTo(7));
        assertThat(seen.get().getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
        // the account is looked up once
        verify(accounts, times(1)).findOrCreateOidc(any(), any(), any(), any());
    }

    @Test
    void remoteVisitorsAreNot() throws IOException, ServletException {
        SingleUserFilter filter = new SingleUserFilter(standalone(true), accounts);

        filter.doFilter(request("192.168.1.20"), new MockHttpServletResponse(), chain());

        assertThat(seen.get()).isNull();
    }

    @Test
    void nobodyIsTheSingleUserWithSeveralUsers() throws IOException, ServletException {
        SingleUserFilter filter = new SingleUserFilter(standalone(false), accounts);

        filter.doFilter(request("127.0.0.1"), new MockHttpServletResponse(), chain());

        assertThat(seen.get()).isNull();
    }

    @Test
    void loggedInUsersStayThemselves() throws IOException, ServletException {
        Authentication alice = UsernamePasswordAuthenticationToken.authenticated("alice", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(alice);
        SingleUserFilter filter = new SingleUserFilter(standalone(true), accounts);

        filter.doFilter(request("127.0.0.1"), new MockHttpServletResponse(), chain());

        assertThat(seen.get()).isSameAs(alice);
    }

    private Standalone standalone(boolean alone) {
        Standalone standalone = new Standalone(true, false, false, config);
        standalone.completeSetup(alone, alone ? null : "alice");
        return standalone;
    }

    private static MockHttpServletRequest request(String address) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/lists");
        request.setRemoteAddr(address);
        return request;
    }

    private MockFilterChain chain() {
        return new MockFilterChain() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response) {
                seen.set(SecurityContextHolder.getContext().getAuthentication());
            }
        };
    }
}
