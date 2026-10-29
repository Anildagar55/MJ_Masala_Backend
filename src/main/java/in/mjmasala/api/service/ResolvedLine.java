package in.mjmasala.api.service;

import in.mjmasala.api.domain.Combo;
import in.mjmasala.api.domain.ItemType;
import in.mjmasala.api.domain.Product;
import in.mjmasala.api.domain.ProductPack;

import java.math.BigDecimal;

/**
 * A cart or order line after the server has resolved it against the catalogue.
 *
 * The price here always comes from the database row, never from the request body — that
 * is the whole point of resolving rather than trusting the client.
 */
public record ResolvedLine(
        ItemType itemType,
        Product product,
        ProductPack pack,
        Combo combo,
        String name,
        String packLabel,
        String slug,
        BigDecimal unitPrice,
        int quantity) {

    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public Long productId() {
        return product != null ? product.getId() : null;
    }

    public Long comboId() {
        return combo != null ? combo.getId() : null;
    }
}
