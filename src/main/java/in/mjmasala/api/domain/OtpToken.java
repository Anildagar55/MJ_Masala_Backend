package in.mjmasala.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A one-time code, stored hashed.
 *
 * The plain code exists only in the outbound message. What is kept here is a BCrypt hash,
 * so a database leak does not hand anyone the ability to verify an account or reset a
 * password. Codes are single-use, expire, and stop being accepted after a few wrong
 * attempts — the three properties that make an OTP worth having.
 */
@Entity
@Table(name = "otp_token")
public class OtpToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 20)
    private String purpose;

    @Column(nullable = false, length = 190)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(nullable = false)
    private boolean consumed = false;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt = Instant.now();

    public boolean isUsable(Instant now) {
        return !consumed && expiresAt.isAfter(now);
    }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }

    public String getCodeHash() { return codeHash; }
    public void setCodeHash(String codeHash) { this.codeHash = codeHash; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }

    public boolean isConsumed() { return consumed; }
    public void setConsumed(boolean consumed) { this.consumed = consumed; }

    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }
}
