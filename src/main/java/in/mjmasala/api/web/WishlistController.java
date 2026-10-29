package in.mjmasala.api.web;

import in.mjmasala.api.domain.ItemType;
import in.mjmasala.api.security.SecurityUser;
import in.mjmasala.api.service.WishlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/wishlist")
@Tag(name = "Wishlist", description = "Saved products and combos")
public class WishlistController {

    private final WishlistService wishlist;

    public WishlistController(WishlistService wishlist) {
        this.wishlist = wishlist;
    }

    @GetMapping
    @Operation(summary = "Everything this account has saved")
    public List<Map<String, Object>> list(@AuthenticationPrincipal SecurityUser principal) {
        return wishlist.list(principal.getId());
    }

    @PostMapping
    @Operation(summary = "Save a product or a combo")
    public List<Map<String, Object>> add(@RequestParam ItemType itemType,
                                         @RequestParam(required = false) Long productId,
                                         @RequestParam(required = false) Long comboId,
                                         @AuthenticationPrincipal SecurityUser principal) {
        return wishlist.add(principal.getId(), itemType, productId, comboId);
    }

    @DeleteMapping("/{itemId}")
    @Operation(summary = "Remove a saved item")
    public List<Map<String, Object>> remove(@PathVariable Long itemId,
                                            @AuthenticationPrincipal SecurityUser principal) {
        return wishlist.remove(principal.getId(), itemId);
    }

    @GetMapping("/status")
    @Operation(summary = "Is this product or combo already saved?")
    public Map<String, Boolean> status(@RequestParam(required = false) Long productId,
                                       @RequestParam(required = false) Long comboId,
                                       @AuthenticationPrincipal SecurityUser principal) {
        return wishlist.status(principal.getId(), productId, comboId);
    }
}
