package in.mjmasala.api.service;

import in.mjmasala.api.domain.Combo;
import in.mjmasala.api.domain.Coupon;
import in.mjmasala.api.domain.ItemType;
import in.mjmasala.api.domain.Order;
import in.mjmasala.api.domain.OrderItem;
import in.mjmasala.api.domain.OrderStatus;
import in.mjmasala.api.domain.PaymentMethod;
import in.mjmasala.api.domain.PaymentStatus;
import in.mjmasala.api.domain.Product;
import in.mjmasala.api.domain.ProductPack;
import in.mjmasala.api.domain.User;
import in.mjmasala.api.dto.order.CreateOrderRequest;
import in.mjmasala.api.dto.order.OrderDto;
import in.mjmasala.api.dto.order.OrderLineDto;
import in.mjmasala.api.dto.order.OrderLineRequest;
import in.mjmasala.api.exception.BadRequestException;
import in.mjmasala.api.exception.NotFoundException;
import in.mjmasala.api.repository.ComboRepository;
import in.mjmasala.api.repository.OrderRepository;
import in.mjmasala.api.repository.ProductPackRepository;
import in.mjmasala.api.repository.UserRepository;
import in.mjmasala.api.util.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Placing and reading orders.
 *
 * The money is computed here from catalogue rows — the request carries only which SKUs and
 * how many. A tampered payload can change what is bought, never what it costs.
 */
@Service
public class OrderService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;
    private static final DateTimeFormatter REF_DAY =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneId.of("Asia/Kolkata"));
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OrderRepository orders;
    private final UserRepository users;
    private final ProductPackRepository packs;
    private final ComboRepository combos;
    private final CartService cart;
    private final CouponService coupons;
    private final PricingService pricing;

    public OrderService(OrderRepository orders, UserRepository users, ProductPackRepository packs,
                        ComboRepository combos, CartService cart, CouponService coupons,
                        PricingService pricing) {
        this.orders = orders;
        this.users = users;
        this.packs = packs;
        this.combos = combos;
        this.cart = cart;
        this.coupons = coupons;
        this.pricing = pricing;
    }

    @Transactional
    public OrderDto create(Long userId, CreateOrderRequest request) {
        User user = users.findById(userId).orElseThrow(() -> NotFoundException.of("User", userId));

        List<ResolvedLine> lines = request.items() == null || request.items().isEmpty()
                ? cart.resolveStored(userId)
                : request.items().stream().map(this::resolve).toList();

        if (lines.isEmpty()) {
            throw new BadRequestException("There is nothing to order yet");
        }

        BigDecimal subtotal = lines.stream()
                .map(ResolvedLine::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Coupon coupon = null;
        BigDecimal discount = Money.ZERO;
        if (request.couponCode() != null && !request.couponCode().isBlank()) {
            coupon = coupons.requireUsable(request.couponCode(), subtotal);
            discount = coupons.discountFor(coupon, subtotal);
        }

        PricingService.Breakdown money = pricing.price(subtotal, discount, request.paymentMethod());

        Order order = new Order();
        order.setReference(nextReference());
        order.setUser(user);
        order.setStatus(OrderStatus.PLACED);
        order.setPaymentMethod(request.paymentMethod());
        // Nothing is actually charged in this build; the gateway callback flips this.
        order.setPaymentStatus(request.paymentMethod() == PaymentMethod.COD
                ? PaymentStatus.PENDING : PaymentStatus.PENDING);

        order.setContactName(request.contactName());
        order.setContactPhone(request.contactPhone());
        order.setContactEmail(request.contactEmail());
        order.setShipLine(request.shipLine());
        order.setShipCity(request.shipCity());
        order.setShipState(request.shipState());
        order.setShipPin(request.shipPin());

        order.setSubtotal(money.subtotal());
        order.setDiscount(money.discount());
        order.setShippingFee(money.shippingFee());
        order.setCodFee(money.codFee());
        order.setTotal(money.total());
        order.setCouponCode(coupon != null ? coupon.getCode() : null);
        order.setNotes(request.notes());
        order.setPlacedAt(Instant.now());

        List<OrderItem> items = new ArrayList<>();
        for (ResolvedLine line : lines) {
            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setItemType(line.itemType());
            item.setProduct(line.product());
            item.setCombo(line.combo());
            item.setName(line.name());
            item.setPackLabel(line.packLabel());
            item.setUnitPrice(line.unitPrice());
            item.setQuantity(line.quantity());
            item.setLineTotal(Money.round(line.lineTotal()));
            items.add(item);
        }
        order.setItems(items);

        Order saved = orders.save(order);

        if (coupon != null) {
            coupons.recordRedemption(coupon);
        }
        if (request.items() == null || request.items().isEmpty()) {
            cart.clear(userId);
        }

        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public OrderDto mine(Long userId, String reference) {
        return orders.findByReferenceAndUserId(reference, userId)
                .map(this::toDto)
                .orElseThrow(() -> new NotFoundException("We could not find that order"));
    }

    @Transactional(readOnly = true)
    public List<OrderDto> listMine(Long userId) {
        return orders.findByUserIdOrderByPlacedAtDesc(userId).stream().map(this::toDto).toList();
    }

    /* ----------------------------- admin ----------------------------- */

    @Transactional(readOnly = true)
    public List<OrderDto> listAll() {
        return orders.findAllByOrderByPlacedAtDesc().stream().map(this::toDto).toList();
    }

    @Transactional
    public OrderDto updateStatus(String reference, OrderStatus status, PaymentStatus paymentStatus) {
        Order order = orders.findByReference(reference)
                .orElseThrow(() -> new NotFoundException("We could not find that order"));

        // status may be null when the payments panel only wants to settle the payment
        if (status != null) {
            order.setStatus(status);
        }
        if (paymentStatus != null) {
            order.setPaymentStatus(paymentStatus);
        } else if (status == OrderStatus.DELIVERED
                && order.getPaymentMethod() == PaymentMethod.COD) {
            order.setPaymentStatus(PaymentStatus.PAID);   // cash collected on delivery
        }
        return toDto(orders.save(order));
    }

    /* ----------------------------- helpers ----------------------------- */

    private ResolvedLine resolve(OrderLineRequest line) {
        if (line.itemType() == ItemType.COMBO) {
            if (line.comboId() == null) throw new BadRequestException("comboId is required");
            Combo combo = combos.findById(line.comboId())
                    .orElseThrow(() -> NotFoundException.of("Combo", line.comboId()));
            return new ResolvedLine(ItemType.COMBO, null, null, combo, combo.getName(),
                    "Box", combo.getSlug(), combo.getPrice(), line.quantity());
        }
        if (line.packId() == null) throw new BadRequestException("packId is required");
        ProductPack pack = packs.findById(line.packId())
                .orElseThrow(() -> NotFoundException.of("Pack", line.packId()));
        Product product = pack.getProduct();
        if (line.productId() != null && !line.productId().equals(product.getId())) {
            throw new BadRequestException("That pack does not belong to the product you sent");
        }
        return new ResolvedLine(ItemType.PRODUCT, product, pack, null, product.getName(),
                pack.getLabel(), product.getSlug(), pack.getPrice(), line.quantity());
    }

    /** MJ-20261021-4865, unique in practice and checked against the table anyway. */
    private String nextReference() {
        for (int attempt = 0; attempt < 8; attempt++) {
            String candidate = "MJ-" + REF_DAY.format(Instant.now()) + "-"
                    + (1000 + RANDOM.nextInt(9000));
            if (orders.findByReference(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not allocate an order reference");
    }

    public OrderDto toDto(Order order) {
        List<OrderLineDto> lines = order.getItems().stream().map(i -> new OrderLineDto(
                i.getItemType().name(),
                i.getName(),
                i.getPackLabel(),
                i.getProduct() != null ? i.getProduct().getSlug()
                        : i.getCombo() != null ? i.getCombo().getSlug() : null,
                i.getUnitPrice(),
                i.getQuantity(),
                i.getLineTotal())).toList();

        return new OrderDto(
                order.getId(),
                order.getReference(),
                order.getStatus().name(),
                order.getPaymentMethod().name(),
                order.getPaymentStatus().name(),
                order.getPlacedAt() != null ? ISO.format(order.getPlacedAt()) : null,
                order.getContactName(),
                order.getContactPhone(),
                order.getContactEmail(),
                order.getShipLine(),
                order.getShipCity(),
                order.getShipState(),
                order.getShipPin(),
                order.getSubtotal(),
                order.getDiscount(),
                order.getShippingFee(),
                order.getCodFee(),
                order.getTotal(),
                order.getCouponCode(),
                lines);
    }
}
