package in.mjmasala.api.service;

import in.mjmasala.api.domain.CartItem;
import in.mjmasala.api.domain.Combo;
import in.mjmasala.api.domain.ItemType;
import in.mjmasala.api.domain.Product;
import in.mjmasala.api.domain.ProductPack;
import in.mjmasala.api.domain.User;
import in.mjmasala.api.dto.cart.AddToCartRequest;
import in.mjmasala.api.dto.cart.CartDto;
import in.mjmasala.api.dto.cart.CartLineDto;
import in.mjmasala.api.exception.BadRequestException;
import in.mjmasala.api.exception.NotFoundException;
import in.mjmasala.api.repository.CartItemRepository;
import in.mjmasala.api.repository.ComboRepository;
import in.mjmasala.api.repository.ProductPackRepository;
import in.mjmasala.api.repository.ProductRepository;
import in.mjmasala.api.repository.UserRepository;
import in.mjmasala.api.util.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The signed-in customer's basket, kept server-side so it follows them between devices.
 *
 * Prices are read from the catalogue on every read, so a basket opened after a price
 * change shows the new price rather than a stale one — the frozen price only happens at
 * order time.
 */
@Service
public class CartService {

    private static final int MAX_QUANTITY = 50;

    private final CartItemRepository items;
    private final ProductRepository products;
    private final ProductPackRepository packs;
    private final ComboRepository combos;
    private final UserRepository users;

    public CartService(CartItemRepository items, ProductRepository products,
                       ProductPackRepository packs, ComboRepository combos, UserRepository users) {
        this.items = items;
        this.products = products;
        this.packs = packs;
        this.combos = combos;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public CartDto get(Long userId) {
        return toDto(items.findByUserIdOrderByIdAsc(userId));
    }

    @Transactional
    public CartDto add(Long userId, AddToCartRequest request) {
        User user = requireUser(userId);
        int qty = request.quantity() == null ? 1 : request.quantity();

        if (request.itemType() == ItemType.COMBO) {
            Combo combo = combos.findById(require(request.comboId(), "comboId"))
                    .orElseThrow(() -> NotFoundException.of("Combo", request.comboId()));
            CartItem line = items.findByUserIdAndComboId(userId, combo.getId()).orElseGet(CartItem::new);
            line.setUser(user);
            line.setItemType(ItemType.COMBO);
            line.setCombo(combo);
            line.setProduct(null);
            line.setPack(null);
            line.setQuantity(clamp(line.getId() == null ? qty : line.getQuantity() + qty));
            items.save(line);
            return toDto(items.findByUserIdOrderByIdAsc(userId));
        }

        ProductPack pack = packs.findById(require(request.packId(), "packId"))
                .orElseThrow(() -> NotFoundException.of("Pack", request.packId()));
        Product product = pack.getProduct();
        if (request.productId() != null && !Objects.equals(request.productId(), product.getId())) {
            throw new BadRequestException("That pack does not belong to the product you sent");
        }

        CartItem line = items.findByUserIdAndPackId(userId, pack.getId()).orElseGet(CartItem::new);
        line.setUser(user);
        line.setItemType(ItemType.PRODUCT);
        line.setProduct(product);
        line.setPack(pack);
        line.setCombo(null);
        line.setQuantity(clamp(line.getId() == null ? qty : line.getQuantity() + qty));
        items.save(line);
        return toDto(items.findByUserIdOrderByIdAsc(userId));
    }

    @Transactional
    public CartDto updateQuantity(Long userId, Long lineId, int quantity) {
        CartItem line = items.findByIdAndUserId(lineId, userId)
                .orElseThrow(() -> NotFoundException.of("Cart line", lineId));
        if (quantity <= 0) {
            items.delete(line);
        } else {
            line.setQuantity(clamp(quantity));
            items.save(line);
        }
        return toDto(items.findByUserIdOrderByIdAsc(userId));
    }

    @Transactional
    public CartDto remove(Long userId, Long lineId) {
        CartItem line = items.findByIdAndUserId(lineId, userId)
                .orElseThrow(() -> NotFoundException.of("Cart line", lineId));
        items.delete(line);
        return toDto(items.findByUserIdOrderByIdAsc(userId));
    }

    @Transactional
    public void clear(Long userId) {
        items.deleteByUserId(userId);
    }

    /** Used by the order flow: resolves the stored cart into priced lines. */
    @Transactional(readOnly = true)
    public List<ResolvedLine> resolveStored(Long userId) {
        return items.findByUserIdOrderByIdAsc(userId).stream().map(line -> {
            if (line.getItemType() == ItemType.COMBO && line.getCombo() != null) {
                Combo combo = line.getCombo();
                return new ResolvedLine(ItemType.COMBO, null, null, combo, combo.getName(),
                        "Box", combo.getSlug(), combo.getPrice(), line.getQuantity());
            }
            ProductPack pack = line.getPack();
            Product product = line.getProduct();
            return new ResolvedLine(ItemType.PRODUCT, product, pack, null, product.getName(),
                    pack.getLabel(), product.getSlug(), pack.getPrice(), line.getQuantity());
        }).toList();
    }

    public static CartDto toDto(List<CartItem> lines) {
        List<CartLineDto> mapped = lines.stream().map(line -> {
            boolean combo = line.getItemType() == ItemType.COMBO && line.getCombo() != null;
            BigDecimal unit = combo ? line.getCombo().getPrice() : line.getPack().getPrice();
            BigDecimal mrp = combo ? line.getCombo().getMrp() : line.getPack().getMrp();
            BigDecimal total = unit.multiply(BigDecimal.valueOf(line.getQuantity()));
            return new CartLineDto(
                    line.getId(),
                    line.getItemType().name(),
                    combo ? null : line.getProduct().getId(),
                    combo ? line.getCombo().getId() : null,
                    combo ? line.getCombo().getSlug() : line.getProduct().getSlug(),
                    combo ? line.getCombo().getName() : line.getProduct().getName(),
                    combo ? "Box" : line.getPack().getLabel(),
                    combo ? line.getCombo().getImageKey() : line.getProduct().getImageKey(),
                    unit,
                    mrp,
                    line.getQuantity(),
                    Money.round(total));
        }).toList();

        BigDecimal subtotal = mapped.stream()
                .map(CartLineDto::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CartDto(mapped, mapped.size(), Money.round(subtotal));
    }

    private int clamp(int quantity) {
        return Math.min(Math.max(1, quantity), MAX_QUANTITY);
    }

    private Long require(Long value, String field) {
        if (value == null) {
            throw new BadRequestException(field + " is required");
        }
        return value;
    }

    private User requireUser(Long userId) {
        return users.findById(userId).orElseThrow(() -> NotFoundException.of("User", userId));
    }

    @Transactional(readOnly = true)
    public Optional<CartItem> line(Long userId, Long lineId) {
        return items.findByIdAndUserId(lineId, userId);
    }
}
