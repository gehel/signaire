package fr.ledcom.signaire.web;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

/** The administrators: the usernames listed in {@code signaire.accounts.admins}. */
@Component
public class AdminUsers {

    static final String ADMIN_ROLE = "ADMIN";
    private static final GrantedAuthority USER = new SimpleGrantedAuthority("ROLE_USER");
    private static final GrantedAuthority ADMIN = new SimpleGrantedAuthority("ROLE_" + ADMIN_ROLE);

    private final Set<String> usernames;

    public AdminUsers(@Value("${signaire.accounts.admins:}") List<String> usernames) {
        this.usernames = usernames.stream()
                .map(username -> username.strip().toLowerCase(Locale.ROOT))
                .filter(username -> !username.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isAdmin(String username) {
        return username != null && usernames.contains(username.toLowerCase(Locale.ROOT));
    }

    /** The roles of a user: every user, and administrators. */
    List<GrantedAuthority> authorities(String username) {
        return isAdmin(username) ? List.of(USER, ADMIN) : List.of(USER);
    }
}
