package fr.ledcom.signaire.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;

import fr.ledcom.signaire.accounts.AccountService;

/** Searching and reading signs is open to all; word lists need an account. */
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(requests -> requests
                        .requestMatchers("/lists/**", "/signs/*/lists/**").authenticated()
                        .anyRequest().permitAll())
                .formLogin(login -> login.loginPage("/login").defaultSuccessUrl("/"))
                .logout(logout -> logout.logoutSuccessUrl("/"));
        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(AccountService accounts) {
        return username -> accounts.findLocalCredentials(username)
                .map(LocalUser::new)
                .orElseThrow(() -> new UsernameNotFoundException("No local account " + username));
    }
}
