package in.mjmasala.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A wholesale quote request or a contact-form message. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "enquiry")
public class Enquiry extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EnquiryKind kind = EnquiryKind.CONTACT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EnquiryStatus status = EnquiryStatus.NEW;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 190)
    private String email;

    @Column(length = 20)
    private String phone;

    /** Business name, for wholesale enquiries. */
    @Column(length = 160)
    private String company;

    @Column(length = 80)
    private String city;

    /** Monthly volume the buyer expects, for wholesale enquiries. */
    @Column(name = "monthly_volume", length = 60)
    private String monthlyVolume;

    @Column(columnDefinition = "text")
    private String message;

    @Column(name = "internal_note", columnDefinition = "text")
    private String internalNote;
}
