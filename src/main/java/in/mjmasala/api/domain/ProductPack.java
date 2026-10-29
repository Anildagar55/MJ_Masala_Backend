package in.mjmasala.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** One rung of the pack ladder — 100 g / 200 g / 500 g / 1 kg. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "product_pack", uniqueConstraints = @UniqueConstraint(name = "uk_pack_sku", columnNames = "sku"))
public class ProductPack extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Display label as the frontend prints it, e.g. {@code "500 g"}. */
    @Column(name = "pack_label", nullable = false, length = 30)
    private String label;

    /** The same weight in grams, so carts and orders can reason about it. */
    @Column(name = "weight_grams", nullable = false)
    private int weightGrams;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /** List price, struck through on the card. Always >= price. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal mrp;

    @Column(nullable = false, length = 40)
    private String sku;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;
}
