package in.mjmasala.api.web;

import in.mjmasala.api.dto.coupon.CouponResultDto;
import in.mjmasala.api.dto.coupon.CouponValidateRequest;
import in.mjmasala.api.dto.shipping.PincodeDto;
import in.mjmasala.api.service.CouponService;
import in.mjmasala.api.service.PricingService;
import in.mjmasala.api.service.ShippingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

/** Small public helpers the cart and checkout call before an order exists. */
@RestController
@Tag(name = "Checkout helpers", description = "Coupon preview, pincode check, delivery rules")
public class CheckoutController {

    private final CouponService coupons;
    private final ShippingService shipping;
    private final PricingService pricing;

    public CheckoutController(CouponService coupons, ShippingService shipping, PricingService pricing) {
        this.coupons = coupons;
        this.shipping = shipping;
        this.pricing = pricing;
    }

    @PostMapping("/coupons/validate")
    @Operation(summary = "Preview a coupon against a subtotal — never throws")
    public CouponResultDto validate(@Valid @RequestBody CouponValidateRequest request) {
        return coupons.validate(request.code(), request.subtotal());
    }

    @GetMapping("/shipping/pincode/{pin}")
    @Operation(summary = "Serviceability and ETA for a pin code")
    public PincodeDto pincode(@PathVariable String pin) {
        return shipping.check(pin);
    }

    @GetMapping("/shipping/rules")
    @Operation(summary = "Free-delivery threshold and fees, so the UI never hard-codes them")
    public Map<String, BigDecimal> rules() {
        return Map.of("freeDeliveryAbove", pricing.freeDeliveryAbove());
    }
}
