package in.mjmasala.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A saved delivery address belonging to an account. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "address")
public class Address extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(length = 40)
    private String label;

    @Column(name = "address_line", nullable = false, length = 400)
    private String line;

    @Column(nullable = false, length = 80)
    private String city;

    @Column(nullable = false, length = 80)
    private String state;

    @Column(nullable = false, length = 10)
    private String pin;

    @Column(length = 20)
    private String phone;

    @Column(name = "is_default", nullable = false)
    private boolean defaultAddress = false;
}
