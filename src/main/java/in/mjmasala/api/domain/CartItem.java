package in.mjmasala.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A server-side cart line, so a signed-in customer's basket follows them across devices
 * instead of living in one browser's localStorage.
 *
 * A line points at either a product pack or a combo — {@code itemType} says which, and
 * exactly one of the two foreign keys is set. The service layer enforces that pairing.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "cart_item")
public class CartItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 20)
    private ItemType itemType = ItemType.PRODUCT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pack_id")
    private ProductPack pack;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "combo_id")
    private Combo combo;

    @Column(nullable = false)
    private int quantity = 1;
}
