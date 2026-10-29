package in.mjmasala.api.web;

import in.mjmasala.api.dto.order.CreateOrderRequest;
import in.mjmasala.api.dto.order.OrderDto;
import in.mjmasala.api.security.SecurityUser;
import in.mjmasala.api.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Orders. A customer can only ever see their own — the lookup is by reference *and* user
 * id, so a guessed reference returns 404 rather than someone else's address.
 */
@RestController
@RequestMapping("/orders")
@Tag(name = "Orders", description = "Place and read orders")
public class OrderController {

    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    @PostMapping
    @Operation(summary = "Place an order — the server prices it, the request only names SKUs")
    public ResponseEntity<OrderDto> create(@Valid @RequestBody CreateOrderRequest request,
                                           @AuthenticationPrincipal SecurityUser principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orders.create(principal.getId(), request));
    }

    @GetMapping("/mine")
    @Operation(summary = "This account's orders, newest first")
    public List<OrderDto> mine(@AuthenticationPrincipal SecurityUser principal) {
        return orders.listMine(principal.getId());
    }

    @GetMapping("/{reference}")
    @Operation(summary = "One order by its reference")
    public OrderDto byReference(@PathVariable String reference,
                                @AuthenticationPrincipal SecurityUser principal) {
        return orders.mine(principal.getId(), reference);
    }
}
