package in.mjmasala.api.service;

import in.mjmasala.api.config.AppProperties;
import in.mjmasala.api.domain.PaymentMethod;
import in.mjmasala.api.util.Money;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * The single place an order's money is worked out.
 *
 * Cart totals, the coupon preview and the order itself all come through here, so the
 * number the customer sees in the cart is by construction the number they are charged.
 * Nothing here ever reads a price sent by the client.
 */
@Service
public class PricingService {

    public record Breakdown(BigDecimal subtotal,
                            BigDecimal discount,
                            BigDecimal shippingFee,
                            BigDecimal codFee,
                            BigDecimal total) {
    }

    private final AppProperties.Shipping rules;

    public PricingService(AppProperties properties) {
        this.rules = properties.shipping();
    }

    public BigDecimal freeDeliveryAbove() {
        return rules.freeAbove();
    }

    /**
     * Delivery is charged on the pre-discount subtotal — matching what the storefront has
     * always shown — and cash on delivery adds its handling fee on top.
     */
    public Breakdown price(BigDecimal subtotal, BigDecimal discount, PaymentMethod method) {
        BigDecimal sub = Money.round(subtotal == null ? BigDecimal.ZERO : subtotal);
        BigDecimal off = Money.round(discount == null ? BigDecimal.ZERO : discount);
        if (off.compareTo(sub) > 0) {
            off = sub;   // never discount below zero
        }

        BigDecimal shipping = sub.compareTo(BigDecimal.ZERO) == 0
                ? Money.ZERO
                : (sub.compareTo(rules.freeAbove()) >= 0 ? Money.ZERO : Money.round(rules.standardFee()));

        BigDecimal cod = method == PaymentMethod.COD ? Money.round(rules.codFee()) : Money.ZERO;

        BigDecimal total = Money.round(sub.subtract(off).add(shipping).add(cod));
        if (total.compareTo(BigDecimal.ZERO) < 0) {
            total = Money.ZERO;
        }
        return new Breakdown(sub, off, shipping, cod, total);
    }
}
