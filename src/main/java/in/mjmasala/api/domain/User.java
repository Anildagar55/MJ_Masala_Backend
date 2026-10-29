package in.mjmasala.api.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * A storefront account. Named {@code app_user} because {@code user} is a reserved word
 * in both PostgreSQL and MySQL.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "app_user", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_email", columnNames = "email"),
        @UniqueConstraint(name = "uk_user_phone", columnNames = "phone")
})
public class User extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 190)
    private String email;

    /**
     * Unique so two accounts cannot claim the same number — it is a sign-in-adjacent
     * identifier and a password reset can be started from it. Nullable because the seeded
     * admin account has none; both databases allow many NULLs in a unique index.
     */
    @Column(length = 20, unique = true)
    private String phone;

    /** BCrypt hash. Never the password itself, and never returned by any endpoint. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.USER;

    @Column(nullable = false)
    private boolean enabled = true;

    /** When the password last changed. For the record and for support, not for security. */
    @Column(name = "password_changed_at")
    private java.time.Instant passwordChangedAt;

    /**
     * Bumped on every password change; carried in the tokens.
     *
     * This is what actually ends old sessions. Comparing timestamps instead would be
     * unreliable: a JWT's {@code iat} only has second resolution, so a token minted in the
     * same second as the change is neither clearly before nor clearly after it. A counter
     * has no such ambiguity — a token from before the bump simply carries a lower number.
     */
    @Column(name = "token_version", nullable = false)
    private int tokenVersion = 0;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Address> addresses = new ArrayList<>();
}
