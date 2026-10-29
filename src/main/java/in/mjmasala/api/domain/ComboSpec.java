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

/** A labelled fact on the combo page, e.g. Jars → "6 × 100 g". */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "combo_spec")
public class ComboSpec extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "combo_id", nullable = false)
    private Combo combo;

    @Column(name = "spec_label", nullable = false, length = 80)
    private String label;

    @Column(name = "spec_value", nullable = false, length = 200)
    private String value;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;
}
