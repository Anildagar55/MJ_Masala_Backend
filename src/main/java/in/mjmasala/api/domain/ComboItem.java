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

/** One jar inside a combo box. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "combo_item")
public class ComboItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "combo_id", nullable = false)
    private Combo combo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Weight of the jar in this box, e.g. {@code "100 g"}. */
    @Column(name = "pack_label", nullable = false, length = 30)
    private String packLabel;

    @Column(nullable = false)
    private int quantity = 1;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;
}
