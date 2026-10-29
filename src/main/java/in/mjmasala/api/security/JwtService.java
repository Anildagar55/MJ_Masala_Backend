package in.mjmasala.api.security;

import in.mjmasala.api.config.AppProperties;
import in.mjmasala.api.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Issues and verifies the JWTs.
 *
 * The subject is the user id; email and role ride along as claims so the filter can build
 * an Authentication without a database hit on every request. The role claim is a
 * convenience only — authorities are re-derived from the token, and anything sensitive is
 * still checked against the database.
 */
@Service
public class JwtService {

    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_TYPE = "typ";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";
    /**
     * A short-lived token that names an account mid-verification. It grants nothing on its
     * own — the API rejects it everywhere except the OTP endpoints — so a leaked one is
     * worth far less than an access token.
     */
    public static final String TYPE_PENDING = "pending";
    public static final String CLAIM_PURPOSE = "purpose";
    public static final String CLAIM_VERSION = "ver";

    private final SecretKey key;
    private final AppProperties.Jwt config;

    public JwtService(AppProperties properties) {
        this.config = properties.jwt();
        byte[] secret = config.secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret must be at least 32 bytes for HS256 — set JWT_SECRET in the environment");
        }
        this.key = Keys.hmacShaKeyFor(secret);
    }

    public String issueAccessToken(User user) {
        return issue(user, TYPE_ACCESS, config.accessTokenTtl());
    }

    public String issueRefreshToken(User user) {
        return issue(user, TYPE_REFRESH, config.refreshTokenTtl());
    }

    public long accessTokenSeconds() {
        return config.accessTokenTtl().toSeconds();
    }

    /** Names an account awaiting OTP verification, for one purpose only. */
    public String issuePendingToken(User user, String purpose, java.time.Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .issuer(config.issuer())
                .claim(CLAIM_PURPOSE, purpose)
                .claim(CLAIM_TYPE, TYPE_PENDING)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    public boolean isPendingToken(Claims claims) {
        return TYPE_PENDING.equals(claims.get(CLAIM_TYPE, String.class));
    }

    public String purpose(Claims claims) {
        return claims.get(CLAIM_PURPOSE, String.class);
    }

    /** The session generation the token was minted in. */
    public int tokenVersion(Claims claims) {
        Integer v = claims.get(CLAIM_VERSION, Integer.class);
        return v == null ? 0 : v;
    }

    /** When a token was minted — used to reject tokens older than the last password change. */
    public Instant issuedAt(Claims claims) {
        return claims.getIssuedAt().toInstant();
    }

    private String issue(User user, String type, java.time.Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .issuer(config.issuer())
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_ROLE, user.getRole().name())
                .claim(CLAIM_TYPE, type)
                .claim(CLAIM_VERSION, user.getTokenVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    /**
     * @throws JwtException if the signature, issuer or expiry is wrong
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(config.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isAccessToken(Claims claims) {
        return TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class));
    }

    public Long userId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    public String role(Claims claims) {
        return claims.get(CLAIM_ROLE, String.class);
    }
}
