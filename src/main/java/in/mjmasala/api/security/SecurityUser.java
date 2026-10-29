package in.mjmasala.api.security;

import in.mjmasala.api.domain.Role;
import in.mjmasala.api.domain.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * The authenticated principal. Controllers receive it via
 * {@code @AuthenticationPrincipal SecurityUser}, and it carries the user id so a handler
 * never has to look the account up again just to know who is calling.
 */
public class SecurityUser implements UserDetails {

    private final Long id;
    private final String email;
    private final String password;
    private final Role role;
    private final boolean enabled;

    public SecurityUser(Long id, String email, String password, Role role, boolean enabled) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.role = role;
        this.enabled = enabled;
    }

    public static SecurityUser of(User user) {
        return new SecurityUser(user.getId(), user.getEmail(), user.getPasswordHash(),
                user.getRole(), user.isEnabled());
    }

    /** Builds a principal straight from a verified token, avoiding a database read. */
    public static SecurityUser fromToken(Long id, String email, Role role) {
        return new SecurityUser(id, email, "", role, true);
    }

    public Long getId() {
        return id;
    }

    public Role getRole() {
        return role;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
