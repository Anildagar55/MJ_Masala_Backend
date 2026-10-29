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

/** A labelled fact shown in the product page spec table, e.g. Aroma → "Earthy, warm". */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "product_spec")
public class ProductSpec extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "spec_label", nullable = false, length = 80)
    private String label;

    @Column(name = "spec_value", nullable = false, length = 200)
    private String value;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;
}
