package in.mjmasala.api.web;

import in.mjmasala.api.dto.review.CreateReviewRequest;
import in.mjmasala.api.dto.review.ReviewDto;
import in.mjmasala.api.dto.review.ReviewPageDto;
import in.mjmasala.api.dto.review.ReviewSummaryDto;
import in.mjmasala.api.security.SecurityUser;
import in.mjmasala.api.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Reviews. Reading is public; writing needs a token, and one account may review a given
 * product once.
 */
@RestController
@Tag(name = "Reviews", description = "Product reviews, rating summary and helpful votes")
public class ReviewController {

    private final ReviewService reviews;

    public ReviewController(ReviewService reviews) {
        this.reviews = reviews;
    }

    @GetMapping("/products/{productId}/reviews")
    @Operation(summary = "Paged reviews plus the rating summary and star distribution")
    public ReviewPageDto list(@PathVariable Long productId,
                              @RequestParam(required = false, defaultValue = "recent") String sort,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "10") int size,
                              @AuthenticationPrincipal SecurityUser principal) {
        return reviews.page(productId, sort, page, size, principal != null ? principal.getId() : null);
    }

    @GetMapping("/products/{productId}/reviews/summary")
    @Operation(summary = "Just the average, total and 5→1 distribution")
    public ReviewSummaryDto summary(@PathVariable Long productId) {
        return reviews.summary(productId);
    }

    @PostMapping("/products/{productId}/reviews")
    @Operation(summary = "Write a review (one per account per product)")
    public ResponseEntity<ReviewDto> create(@PathVariable Long productId,
                                            @Valid @RequestBody CreateReviewRequest request,
                                            @AuthenticationPrincipal SecurityUser principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reviews.create(productId, request, principal.getId()));
    }

    @DeleteMapping("/reviews/{reviewId}")
    @Operation(summary = "Delete your own review")
    public ResponseEntity<Void> delete(@PathVariable Long reviewId,
                                       @AuthenticationPrincipal SecurityUser principal) {
        reviews.delete(reviewId, principal.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reviews/{reviewId}/helpful")
    @Operation(summary = "Mark a review helpful — idempotent per account")
    public Map<String, Integer> helpful(@PathVariable Long reviewId,
                                        @AuthenticationPrincipal SecurityUser principal) {
        return Map.of("helpfulCount", reviews.markHelpful(reviewId, principal.getId()));
    }
}
