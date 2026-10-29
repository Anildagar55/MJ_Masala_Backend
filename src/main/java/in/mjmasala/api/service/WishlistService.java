package in.mjmasala.api.service;

import in.mjmasala.api.domain.Combo;
import in.mjmasala.api.domain.ItemType;
import in.mjmasala.api.domain.Product;
import in.mjmasala.api.domain.User;
import in.mjmasala.api.domain.WishlistItem;
import in.mjmasala.api.exception.BadRequestException;
import in.mjmasala.api.exception.NotFoundException;
import in.mjmasala.api.repository.ComboRepository;
import in.mjmasala.api.repository.ProductRepository;
import in.mjmasala.api.repository.UserRepository;
import in.mjmasala.api.repository.WishlistItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/** Saved-for-later, per account. */
@Service
public class WishlistService {

    private final WishlistItemRepository wishlist;
    private final ProductRepository products;
    private final ComboRepository combos;
    private final UserRepository users;

    public WishlistService(WishlistItemRepository wishlist, ProductRepository products,
                           ComboRepository combos, UserRepository users) {
        this.wishlist = wishlist;
        this.products = products;
        this.combos = combos;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(Long userId) {
        return wishlist.findByUserIdOrderByIdDesc(userId).stream().map(this::toView).toList();
    }

    @Transactional
    public List<Map<String, Object>> add(Long userId, ItemType itemType, Long productId, Long comboId) {
        User user = users.findById(userId).orElseThrow(() -> NotFoundException.of("User", userId));

        if (itemType == ItemType.COMBO) {
            if (comboId == null) throw new BadRequestException("comboId is required");
            Combo combo = combos.findById(comboId)
                    .orElseThrow(() -> NotFoundException.of("Combo", comboId));
            if (wishlist.findByUserIdAndComboId(userId, comboId).isEmpty()) {
                WishlistItem item = new WishlistItem();
                item.setUser(user);
                item.setItemType(ItemType.COMBO);
                item.setCombo(combo);
                wishlist.save(item);
            }
        } else {
            if (productId == null) throw new BadRequestException("productId is required");
            Product product = products.findById(productId)
                    .orElseThrow(() -> NotFoundException.of("Product", productId));
            if (wishlist.findByUserIdAndProductId(userId, productId).isEmpty()) {
                WishlistItem item = new WishlistItem();
                item.setUser(user);
                item.setItemType(ItemType.PRODUCT);
                item.setProduct(product);
                wishlist.save(item);
            }
        }
        return list(userId);
    }

    @Transactional
    public List<Map<String, Object>> remove(Long userId, Long itemId) {
        WishlistItem item = wishlist.findByIdAndUserId(itemId, userId)
                .orElseThrow(() -> NotFoundException.of("Wishlist item", itemId));
        wishlist.delete(item);
        return list(userId);
    }

    @Transactional(readOnly = true)
    public Map<String, Boolean> status(Long userId, Long productId, Long comboId) {
        boolean saved = productId != null
                ? wishlist.existsByUserIdAndProductId(userId, productId)
                : comboId != null && wishlist.existsByUserIdAndComboId(userId, comboId);
        return Map.of("saved", saved);
    }

    private Map<String, Object> toView(WishlistItem item) {
        boolean combo = item.getItemType() == ItemType.COMBO && item.getCombo() != null;
        if (combo) {
            Combo c = item.getCombo();
            return view(item.getId(), "COMBO", c.getId(), c.getSlug(), c.getName(),
                    c.getImageKey(), c.getPrice(), c.getMrp());
        }
        Product p = item.getProduct();
        return view(item.getId(), "PRODUCT", p.getId(), p.getSlug(), p.getName(),
                p.getImageKey(), p.getMinPrice(), null);
    }

    private Map<String, Object> view(Long id, String type, Long refId, String slug, String name,
                                     String imageKey, java.math.BigDecimal price,
                                     java.math.BigDecimal mrp) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("id", id);
        map.put("itemType", type);
        map.put("refId", refId);
        map.put("slug", slug);
        map.put("name", name);
        map.put("imageKey", imageKey);
        map.put("price", price);
        if (mrp != null) map.put("mrp", mrp);
        return map;
    }
}
