package in.mjmasala.api.web;

import in.mjmasala.api.dto.cart.AddToCartRequest;
import in.mjmasala.api.dto.cart.CartDto;
import in.mjmasala.api.dto.cart.UpdateQuantityRequest;
import in.mjmasala.api.security.SecurityUser;
import in.mjmasala.api.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in customer's cart.
 *
 * Every handler returns the whole cart rather than just the changed line, so the client
 * never has to reconcile totals itself — the server's numbers are the ones shown.
 */
@RestController
@RequestMapping("/cart")
@Tag(name = "Cart", description = "Server-side basket for a signed-in customer")
public class CartController {

    private final CartService cart;

    public CartController(CartService cart) {
        this.cart = cart;
    }

    @GetMapping
    @Operation(summary = "The current cart with totals")
    public CartDto get(@AuthenticationPrincipal SecurityUser principal) {
        return cart.get(principal.getId());
    }

    @PostMapping("/items")
    @Operation(summary = "Add a pack or a combo")
    public CartDto add(@Valid @RequestBody AddToCartRequest request,
                       @AuthenticationPrincipal SecurityUser principal) {
        return cart.add(principal.getId(), request);
    }

    @PatchMapping("/items/{lineId}")
    @Operation(summary = "Change a line's quantity — 0 removes it")
    public CartDto update(@PathVariable Long lineId,
                          @Valid @RequestBody UpdateQuantityRequest request,
                          @AuthenticationPrincipal SecurityUser principal) {
        return cart.updateQuantity(principal.getId(), lineId, request.quantity());
    }

    @DeleteMapping("/items/{lineId}")
    @Operation(summary = "Remove a line")
    public CartDto remove(@PathVariable Long lineId,
                          @AuthenticationPrincipal SecurityUser principal) {
        return cart.remove(principal.getId(), lineId);
    }

    @DeleteMapping
    @Operation(summary = "Empty the cart")
    public ResponseEntity<Void> clear(@AuthenticationPrincipal SecurityUser principal) {
        cart.clear(principal.getId());
        return ResponseEntity.noContent().build();
    }
}
