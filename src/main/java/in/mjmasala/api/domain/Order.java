package in.mjmasala.api.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A placed order.
 *
 * Two things are deliberate here:
 *
 * 1. The delivery address is <em>copied onto the order</em> rather than referenced. If a
 *    customer later edits or deletes the address on their account, the order must still
 *    show where it was actually sent.
 * 2. Prices are copied onto each {@link OrderItem} for the same reason — a later price
 *    change must not rewrite history.
 *
 * {@code user} is nullable so a guest checkout can be recorded and claimed later.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "orders", uniqueConstraints = @UniqueConstraint(name = "uk_order_reference", columnNames = "reference"))
public class Order extends BaseEntity {

    /** Human-facing id, e.g. MJ-20261021-4865. */
    @Column(nullable = false, length = 40)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private OrderStatus status = OrderStatus.PLACED;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 24)
    private PaymentMethod paymentMethod = PaymentMethod.UPI;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 24)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    /* ---- contact + delivery snapshot ---- */
    @Column(name = "contact_name", nullable = false, length = 120)
    private String contactName;

    @Column(name = "contact_phone", nullable = false, length = 20)
    private String contactPhone;

    @Column(name = "contact_email", nullable = false, length = 190)
    private String contactEmail;

    @Column(name = "ship_line", nullable = false, length = 400)
    private String shipLine;

    @Column(name = "ship_city", nullable = false, length = 80)
    private String shipCity;

    @Column(name = "ship_state", nullable = false, length = 80)
    private String shipState;

    @Column(name = "ship_pin", nullable = false, length = 10)
    private String shipPin;

    /* ---- money ---- */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(name = "shipping_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal shippingFee = BigDecimal.ZERO;

    @Column(name = "cod_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal codFee = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    @Column(name = "coupon_code", length = 40)
    private String couponCode;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "placed_at", nullable = false)
    private Instant placedAt = Instant.now();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id asc")
    private List<OrderItem> items = new ArrayList<>();
}
