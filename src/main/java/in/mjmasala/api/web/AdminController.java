package in.mjmasala.api.web;

import in.mjmasala.api.domain.Coupon;
import in.mjmasala.api.domain.EnquiryStatus;
import in.mjmasala.api.dto.admin.AdminComboRequest;
import in.mjmasala.api.dto.admin.AdminProductRequest;
import in.mjmasala.api.dto.admin.CouponPatchRequest;
import in.mjmasala.api.dto.admin.PaymentPatchRequest;
import in.mjmasala.api.dto.admin.PaymentRowDto;
import in.mjmasala.api.dto.admin.RevenueSummaryDto;
import in.mjmasala.api.dto.admin.CouponRequest;
import in.mjmasala.api.dto.admin.EnquiryUpdateRequest;
import in.mjmasala.api.dto.admin.PackPatchRequest;
import in.mjmasala.api.dto.admin.ProductPatchRequest;
import in.mjmasala.api.dto.admin.StatsDto;
import in.mjmasala.api.dto.catalogue.ComboDetailDto;
import in.mjmasala.api.dto.catalogue.ProductDetailDto;
import in.mjmasala.api.dto.enquiry.EnquiryDto;
import in.mjmasala.api.dto.order.OrderDto;
import in.mjmasala.api.dto.order.UpdateOrderStatusRequest;
import in.mjmasala.api.service.AdminCatalogueService;
import in.mjmasala.api.service.AdminService;
import in.mjmasala.api.service.EnquiryService;
import in.mjmasala.api.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Back office. Guarded twice over: the security filter chain restricts {@code /admin/**}
 * to ROLE_ADMIN, and each method carries {@code @PreAuthorize} as well — belt and braces,
 * because a future refactor of the matchers should not silently open this up.
 */
@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin", description = "Orders, catalogue edits, coupons, enquiries and stats")
public class AdminController {

    private final AdminService admin;
    private final AdminCatalogueService catalogue;
    private final OrderService orders;
    private final EnquiryService enquiries;

    public AdminController(AdminService admin, AdminCatalogueService catalogue,
                           OrderService orders, EnquiryService enquiries) {
        this.admin = admin;
        this.catalogue = catalogue;
        this.orders = orders;
        this.enquiries = enquiries;
    }

    @GetMapping("/stats")
    @Operation(summary = "Dashboard numbers")
    public StatsDto stats() {
        return admin.stats();
    }

    /* ------------------------------- orders ------------------------------- */

    @GetMapping("/orders")
    @Operation(summary = "Every order, newest first")
    public List<OrderDto> allOrders() {
        return orders.listAll();
    }

    @PatchMapping("/orders/{reference}")
    @Operation(summary = "Move an order along its status ladder")
    public OrderDto updateOrder(@PathVariable String reference,
                                @Valid @RequestBody UpdateOrderStatusRequest request) {
        return orders.updateStatus(reference, request.status(), request.paymentStatus());
    }

    /* --------------------- catalogue: full create/edit -------------------- */

    @GetMapping("/products")
    @Operation(summary = "Every product, including switched-off ones")
    public List<ProductDetailDto> allProducts() {
        return catalogue.listProducts();
    }

    @GetMapping("/products/{slug}")
    @Operation(summary = "One product for the edit form")
    public ProductDetailDto oneProduct(@PathVariable String slug) {
        return catalogue.getProduct(slug);
    }

    @PostMapping("/products")
    @Operation(summary = "Create a product with its pack ladder")
    public ResponseEntity<ProductDetailDto> createProduct(
            @Valid @RequestBody AdminProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogue.createProduct(request));
    }

    @PutMapping("/products/{slug}")
    @Operation(summary = "Replace a product, its packs and its specs")
    public ProductDetailDto updateProduct(@PathVariable String slug,
                                          @Valid @RequestBody AdminProductRequest request) {
        return catalogue.updateProduct(slug, request);
    }

    @DeleteMapping("/products/{slug}")
    @Operation(summary = "Take a product off the shop (soft delete)")
    public ResponseEntity<Void> deactivateProduct(@PathVariable String slug) {
        catalogue.deactivateProduct(slug);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/combos")
    @Operation(summary = "Every combo, including switched-off ones")
    public List<ComboDetailDto> allCombos() {
        return catalogue.listCombos();
    }

    @GetMapping("/combos/{slug}")
    @Operation(summary = "One combo for the edit form")
    public ComboDetailDto oneCombo(@PathVariable String slug) {
        return catalogue.getCombo(slug);
    }

    @PostMapping("/combos")
    @Operation(summary = "Create a combo box")
    public ResponseEntity<ComboDetailDto> createCombo(
            @Valid @RequestBody AdminComboRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogue.createCombo(request));
    }

    @PutMapping("/combos/{slug}")
    @Operation(summary = "Replace a combo, its price and its contents")
    public ComboDetailDto updateCombo(@PathVariable String slug,
                                      @Valid @RequestBody AdminComboRequest request) {
        return catalogue.updateCombo(slug, request);
    }

    /* -------------------------------- images ------------------------------- */

    @PostMapping(value = "/products/{slug}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a product photograph",
            description = "JPEG, PNG or WebP. Stored under a generated name and shrunk to "
                    + "app.media.max-dimension; the response carries the new imageUrl.")
    public ProductDetailDto uploadProductImage(@PathVariable String slug,
                                               @RequestParam("file") MultipartFile file) {
        return catalogue.setProductImage(slug, file);
    }

    @DeleteMapping("/products/{slug}/image")
    @Operation(summary = "Remove a product photograph")
    public ProductDetailDto removeProductImage(@PathVariable String slug) {
        return catalogue.clearProductImage(slug);
    }

    @PostMapping(value = "/combos/{slug}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a combo photograph")
    public ComboDetailDto uploadComboImage(@PathVariable String slug,
                                           @RequestParam("file") MultipartFile file) {
        return catalogue.setComboImage(slug, file);
    }

    @DeleteMapping("/combos/{slug}/image")
    @Operation(summary = "Remove a combo photograph")
    public ComboDetailDto removeComboImage(@PathVariable String slug) {
        return catalogue.clearComboImage(slug);
    }

    /* ------------------------------ payments ------------------------------ */

    @GetMapping("/payments")
    @Operation(summary = "Every order seen as a payment")
    public List<PaymentRowDto> payments() {
        return admin.listPayments();
    }

    @GetMapping("/payments/summary")
    @Operation(summary = "Collected, pending and refunded totals by method")
    public RevenueSummaryDto revenue() {
        return admin.revenueSummary();
    }

    @PatchMapping("/payments/{reference}")
    @Operation(summary = "Settle, fail or refund an order's payment")
    public OrderDto updatePayment(@PathVariable String reference,
                                  @Valid @RequestBody PaymentPatchRequest request) {
        return orders.updateStatus(reference, null, request.paymentStatus());
    }

    /* ----------------------------- catalogue ----------------------------- */

    @PatchMapping("/products/{slug}")
    @Operation(summary = "Toggle a product or edit its copy")
    public ResponseEntity<Map<String, String>> patchProduct(@PathVariable String slug,
                                                            @Valid @RequestBody ProductPatchRequest request) {
        admin.patchProduct(slug, request);
        return ResponseEntity.ok(Map.of("slug", slug, "status", "updated"));
    }

    @PatchMapping("/packs/{packId}")
    @Operation(summary = "Change a pack's price or MRP")
    public ResponseEntity<Map<String, String>> patchPack(@PathVariable Long packId,
                                                         @Valid @RequestBody PackPatchRequest request) {
        admin.patchPack(packId, request);
        return ResponseEntity.ok(Map.of("packId", String.valueOf(packId), "status", "updated"));
    }

    /* ------------------------------ coupons ------------------------------ */

    @GetMapping("/coupons")
    @Operation(summary = "Active coupons")
    public List<Coupon> coupons() {
        return admin.listCoupons();
    }

    @PostMapping("/coupons")
    @Operation(summary = "Create a coupon")
    public ResponseEntity<Coupon> createCoupon(@Valid @RequestBody CouponRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(admin.createCoupon(request));
    }

    @PatchMapping("/coupons/{id}")
    @Operation(summary = "Edit or switch a coupon off")
    public Coupon patchCoupon(@PathVariable Long id,
                              @Valid @RequestBody CouponPatchRequest request) {
        return admin.patchCoupon(id, request);
    }

    /* ----------------------------- enquiries ----------------------------- */

    @GetMapping("/enquiries")
    @Operation(summary = "Wholesale and contact messages")
    public List<EnquiryDto> enquiries() {
        return enquiries.listAll();
    }

    @PatchMapping("/enquiries/{id}")
    @Operation(summary = "Update how far an enquiry has got")
    public EnquiryDto updateEnquiry(@PathVariable Long id,
                                    @Valid @RequestBody EnquiryUpdateRequest request) {
        return enquiries.updateStatus(id, request.status(), request.note());
    }

    @GetMapping("/enquiries/statuses")
    @Operation(summary = "The status values an enquiry can take")
    public List<String> enquiryStatuses() {
        return java.util.Arrays.stream(EnquiryStatus.values()).map(Enum::name).toList();
    }
}
