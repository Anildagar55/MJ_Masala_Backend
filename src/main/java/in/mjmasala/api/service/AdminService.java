package in.mjmasala.api.service;

import in.mjmasala.api.domain.Coupon;
import in.mjmasala.api.domain.CouponType;
import in.mjmasala.api.domain.EnquiryStatus;
import in.mjmasala.api.domain.OrderStatus;
import in.mjmasala.api.domain.PaymentMethod;
import in.mjmasala.api.domain.PaymentStatus;
import in.mjmasala.api.domain.Product;
import in.mjmasala.api.domain.ProductPack;
import in.mjmasala.api.dto.admin.CouponPatchRequest;
import in.mjmasala.api.dto.admin.PaymentRowDto;
import in.mjmasala.api.dto.admin.RevenueSummaryDto;
import in.mjmasala.api.dto.admin.CouponRequest;
import in.mjmasala.api.dto.admin.PackPatchRequest;
import in.mjmasala.api.dto.admin.ProductPatchRequest;
import in.mjmasala.api.dto.admin.StatsDto;
import in.mjmasala.api.exception.ConflictException;
import in.mjmasala.api.exception.NotFoundException;
import in.mjmasala.api.repository.ComboRepository;
import in.mjmasala.api.repository.CouponRepository;
import in.mjmasala.api.repository.EnquiryRepository;
import in.mjmasala.api.repository.OrderRepository;
import in.mjmasala.api.repository.ProductPackRepository;
import in.mjmasala.api.repository.ProductRepository;
import in.mjmasala.api.repository.ReviewRepository;
import in.mjmasala.api.util.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Back-office operations: the day-to-day edits a shop owner needs — change a pack price,
 * pull a product, start or stop a coupon — plus the numbers on the dashboard.
 */
@Service
public class AdminService {

    private final ProductRepository products;
    private final ProductPackRepository packs;
    private final ComboRepository combos;
    private final CouponRepository coupons;
    private final OrderRepository orders;
    private final ReviewRepository reviews;
    private final EnquiryRepository enquiries;

    public AdminService(ProductRepository products, ProductPackRepository packs,
                        ComboRepository combos, CouponRepository coupons, OrderRepository orders,
                        ReviewRepository reviews, EnquiryRepository enquiries) {
        this.products = products;
        this.packs = packs;
        this.combos = combos;
        this.coupons = coupons;
        this.orders = orders;
        this.reviews = reviews;
        this.enquiries = enquiries;
    }

    /* ------------------------------ products ------------------------------ */

    @Transactional
    public void patchProduct(String slug, ProductPatchRequest request) {
        Product product = products.findBySlugAndActiveTrue(slug)
                .orElseGet(() -> products.findAll().stream()
                        .filter(p -> p.getSlug().equals(slug))
                        .findFirst()
                        .orElseThrow(() -> new NotFoundException("No product with slug " + slug)));
        if (request.active() != null) product.setActive(request.active());
        if (request.bestseller() != null) product.setBestseller(request.bestseller());
        if (request.blurb() != null) product.setBlurb(request.blurb());
        if (request.description() != null) product.setDescription(request.description());
        products.save(product);
    }

    /**
     * Changing a pack price must also refresh the product's cached {@code minPrice} and
     * {@code offPct}, otherwise the shop's price filter starts lying.
     */
    @Transactional
    public void patchPack(Long packId, PackPatchRequest request) {
        ProductPack pack = packs.findById(packId)
                .orElseThrow(() -> NotFoundException.of("Pack", packId));
        if (request.price() != null) pack.setPrice(Money.round(request.price()));
        if (request.mrp() != null) pack.setMrp(Money.round(request.mrp()));
        if (request.active() != null) pack.setActive(request.active());

        if (pack.getMrp().compareTo(pack.getPrice()) < 0) {
            throw new in.mjmasala.api.exception.BadRequestException(
                    "MRP cannot be lower than the selling price");
        }
        packs.save(pack);

        Product product = pack.getProduct();
        products.save(product);
        product.recalculatePackAggregates();
        products.save(product);
    }

    /* ------------------------------ coupons ------------------------------ */

    @Transactional(readOnly = true)
    public List<Coupon> listCoupons() {
        return coupons.findByActiveTrueOrderByIdAsc();
    }

    @Transactional
    public Coupon createCoupon(CouponRequest request) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (coupons.findByCodeIgnoreCase(code).isPresent()) {
            throw new ConflictException("Coupon " + code + " already exists");
        }
        Coupon coupon = new Coupon();
        coupon.setCode(code);
        coupon.setType(request.type() == null ? CouponType.PERCENT : request.type());
        coupon.setValue(Money.round(request.value()));
        coupon.setMinSubtotal(request.minSubtotal() == null ? BigDecimal.ZERO : Money.round(request.minSubtotal()));
        coupon.setMaxDiscount(request.maxDiscount() == null ? null : Money.round(request.maxDiscount()));
        coupon.setActive(request.active() == null || request.active());
        coupon.setExpiresAt(request.expiresAt());
        coupon.setUsageLimit(request.usageLimit());
        coupon.setDescription(request.description());
        validateCouponShape(coupon);
        return coupons.save(coupon);
    }

    @Transactional
    public Coupon patchCoupon(Long id, CouponPatchRequest request) {
        Coupon coupon = coupons.findById(id).orElseThrow(() -> NotFoundException.of("Coupon", id));
        if (request.value() != null) coupon.setValue(Money.round(request.value()));
        if (request.minSubtotal() != null) coupon.setMinSubtotal(Money.round(request.minSubtotal()));
        if (request.maxDiscount() != null) coupon.setMaxDiscount(Money.round(request.maxDiscount()));
        if (request.active() != null) coupon.setActive(request.active());
        if (request.expiresAt() != null) coupon.setExpiresAt(request.expiresAt());
        if (request.usageLimit() != null) coupon.setUsageLimit(request.usageLimit());
        if (request.description() != null) coupon.setDescription(request.description());
        validateCouponShape(coupon);
        return coupons.save(coupon);
    }

    private void validateCouponShape(Coupon coupon) {
        if (coupon.getType() == CouponType.PERCENT
                && coupon.getValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new in.mjmasala.api.exception.BadRequestException(
                    "A percentage coupon cannot exceed 100");
        }
    }

    /* ------------------------------ payments ------------------------------ */

    /**
     * The payments panel.
     *
     * There is no gateway yet, so an order *is* the transaction: this view is the order
     * table seen through a payment lens. When a gateway is added, this is the method that
     * starts reading from a real ledger instead.
     */
    @Transactional(readOnly = true)
    public List<PaymentRowDto> listPayments() {
        return orders.findAllByOrderByPlacedAtDesc().stream()
                .map(o -> new PaymentRowDto(
                        o.getReference(),
                        o.getPlacedAt() != null
                                ? java.time.format.DateTimeFormatter.ISO_INSTANT.format(o.getPlacedAt())
                                : null,
                        o.getContactName(),
                        o.getPaymentMethod().name(),
                        o.getPaymentStatus().name(),
                        o.getTotal(),
                        o.getCodFee(),
                        o.getCouponCode()))
                .toList();
    }

    @Transactional(readOnly = true)
    public RevenueSummaryDto revenueSummary() {
        List<in.mjmasala.api.domain.Order> all = orders.findAll();

        BigDecimal collected = all.stream()
                .filter(o -> o.getPaymentStatus() == PaymentStatus.PAID)
                .map(in.mjmasala.api.domain.Order::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal pending = all.stream()
                .filter(o -> o.getPaymentStatus() == PaymentStatus.PENDING
                        && o.getStatus() != OrderStatus.CANCELLED)
                .map(in.mjmasala.api.domain.Order::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal refunded = all.stream()
                .filter(o -> o.getPaymentStatus() == PaymentStatus.REFUNDED)
                .map(in.mjmasala.api.domain.Order::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        java.util.Map<String, BigDecimal> byMethod = new java.util.LinkedHashMap<>();
        for (PaymentMethod method : PaymentMethod.values()) {
            BigDecimal sum = all.stream()
                    .filter(o -> o.getPaymentMethod() == method
                            && o.getPaymentStatus() != PaymentStatus.REFUNDED
                            && o.getStatus() != OrderStatus.CANCELLED)
                    .map(in.mjmasala.api.domain.Order::getTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            byMethod.put(method.name(), Money.round(sum));
        }

        return new RevenueSummaryDto(Money.round(collected), Money.round(pending),
                Money.round(refunded), byMethod);
    }

    /* ------------------------------- stats ------------------------------- */

    @Transactional(readOnly = true)
    public StatsDto stats() {
        ZonedDateTime startOfDay = ZonedDateTime.now(ZoneId.of("Asia/Kolkata")).toLocalDate().atStartOfDay(ZoneId.of("Asia/Kolkata"));
        Instant from = startOfDay.toInstant();

        List<in.mjmasala.api.domain.Order> all = orders.findAll();
        long today = all.stream()
                .filter(o -> o.getPlacedAt() != null && o.getPlacedAt().isAfter(from))
                .count();
        BigDecimal revenue = all.stream()
                .filter(o -> o.getStatus() != OrderStatus.CANCELLED)
                .map(in.mjmasala.api.domain.Order::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long awaiting = all.stream()
                .filter(o -> o.getStatus() == OrderStatus.PLACED || o.getStatus() == OrderStatus.CONFIRMED)
                .count();

        return new StatsDto(
                today,
                all.size(),
                awaiting,
                Money.round(revenue),
                products.count(),
                combos.count(),
                reviews.count(),
                enquiries.countByStatus(EnquiryStatus.NEW));
    }
}
