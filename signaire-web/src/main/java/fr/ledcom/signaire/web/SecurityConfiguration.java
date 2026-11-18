package fr.ledcom.signaire.web;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;

import fr.ledcom.signaire.accounts.AccountService;

/** Searching and reading signs is open to all; word lists need an account, administration an admin. */
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    /**
     * Local accounts log in with a form. OpenID Connect login is added when OAuth2 clients are configured
     * ({@code spring.security.oauth2.client.registration.*}).
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AccountService accounts, AdminUsers admins,
            ObjectProvider<ClientRegistrationRepository> clientRegistrations) throws Exception {
        http.authorizeHttpRequests(requests -> requests
                        .requestMatchers("/admin/**").hasRole(AdminUsers.ADMIN_ROLE)
                        .requestMatchers("/lists/**", "/signs/*/lists/**", "/requests/**").authenticated()
                        .anyRequest().permitAll())
                .formLogin(login -> login.loginPage("/login").defaultSuccessUrl("/"))
                .logout(logout -> logout.logoutSuccessUrl("/"));
        if (clientRegistrations.getIfAvailable() != null) {
            http.oauth2Login(login -> login.loginPage("/login").defaultSuccessUrl("/")
                    .userInfoEndpoint(userInfo -> userInfo.oidcUserService(new OidcAccounts(accounts, admins))));
        }
        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(AccountService accounts, AdminUsers admins) {
        return username -> accounts.findLocalCredentials(username)
                .map(credentials -> new LocalUser(credentials, admins))
                .orElseThrow(() -> new UsernameNotFoundException("No local account " + username));
    }
}
