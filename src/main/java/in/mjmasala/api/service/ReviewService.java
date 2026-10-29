package in.mjmasala.api.service;

import in.mjmasala.api.domain.OrderStatus;
import in.mjmasala.api.domain.Product;
import in.mjmasala.api.domain.Review;
import in.mjmasala.api.domain.ReviewHelpful;
import in.mjmasala.api.domain.User;
import in.mjmasala.api.dto.common.PageResponse;
import in.mjmasala.api.dto.review.CreateReviewRequest;
import in.mjmasala.api.dto.review.ReviewDto;
import in.mjmasala.api.dto.review.ReviewPageDto;
import in.mjmasala.api.dto.review.ReviewSummaryDto;
import in.mjmasala.api.exception.BadRequestException;
import in.mjmasala.api.exception.NotFoundException;
import in.mjmasala.api.repository.OrderRepository;
import in.mjmasala.api.repository.ProductRepository;
import in.mjmasala.api.repository.ReviewHelpfulRepository;
import in.mjmasala.api.repository.ReviewRepository;
import in.mjmasala.api.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The reviews system.
 *
 * Two invariants are enforced here rather than trusted to the client:
 *  - one review per account per product (the storefront hides the form once you have
 *    written one, but that is a convenience, not the rule);
 *  - the product's average and count are recomputed from the rows after every write, so
 *    the shop's rating filter can never drift from the reviews on the page.
 */
@Service
public class ReviewService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;

    private final ReviewRepository reviews;
    private final ReviewHelpfulRepository helpful;
    private final ProductRepository products;
    private final UserRepository users;
    private final OrderRepository orders;

    public ReviewService(ReviewRepository reviews, ReviewHelpfulRepository helpful,
                         ProductRepository products, UserRepository users, OrderRepository orders) {
        this.reviews = reviews;
        this.helpful = helpful;
        this.products = products;
        this.users = users;
        this.orders = orders;
    }

    @Transactional(readOnly = true)
    public ReviewSummaryDto summary(Long productId) {
        double average = reviews.averageForProduct(productId);
        int total = (int) reviews.countByProductIdAndApprovedTrue(productId);

        Map<Integer, Integer> distribution = new LinkedHashMap<>();
        for (int star = 5; star >= 1; star--) {
            distribution.put(star, 0);          // every bar present, so the UI never guesses
        }
        for (Object[] row : reviews.distributionForProduct(productId)) {
            distribution.put(((Number) row[0]).intValue(), ((Number) row[1]).intValue());
        }

        BigDecimal rounded = BigDecimal.valueOf(average).setScale(1, RoundingMode.HALF_UP);
        return new ReviewSummaryDto(rounded, total, distribution);
    }

    @Transactional(readOnly = true)
    public ReviewPageDto page(Long productId, String sort, int page, int size, Long currentUserId) {
        requireProduct(productId);
        Page<Review> found = reviews.findByProductIdAndApprovedTrue(productId,
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 50), sortFor(sort)));

        PageResponse<ReviewDto> mapped = PageResponse.of(found, r -> toDto(r, currentUserId));
        return new ReviewPageDto(summary(productId), mapped.items(),
                mapped.page(), mapped.size(), mapped.totalItems());
    }

    @Transactional
    public ReviewDto create(Long productId, CreateReviewRequest request, Long userId) {
        Product product = requireProduct(productId);
        User author = users.findById(userId)
                .orElseThrow(() -> NotFoundException.of("User", userId));

        if (reviews.existsByProductIdAndUserId(productId, userId)) {
            throw new BadRequestException("You have already reviewed this spice");
        }

        Review review = new Review();
        review.setProduct(product);
        review.setUser(author);
        review.setAuthorName(author.getName());
        review.setCity(request.city() != null && !request.city().isBlank()
                ? request.city().trim()
                : null);
        review.setRating(request.rating());
        review.setTitle(request.title());
        review.setBody(request.body());
        review.setPhotoUrl(request.photoUrl());
        review.setVerified(orders.hasDeliveredProduct(userId, productId, OrderStatus.DELIVERED));

        Review saved = reviews.save(review);
        recalculate(productId);
        return toDto(saved, userId);
    }

    @Transactional
    public void delete(Long reviewId, Long userId) {
        Review review = reviews.findByIdAndUserId(reviewId, userId)
                .orElseThrow(() -> new NotFoundException("That review is not yours to remove"));
        Long productId = review.getProduct().getId();
        helpful.deleteByReviewId(reviewId);
        reviews.delete(review);
        recalculate(productId);
    }

    /**
     * Idempotent: the unique constraint on (review, user) means a second tap changes
     * nothing rather than inflating the count.
     */
    @Transactional
    public int markHelpful(Long reviewId, Long userId) {
        Review review = reviews.findById(reviewId)
                .orElseThrow(() -> NotFoundException.of("Review", reviewId));

        if (!helpful.existsByReviewIdAndUserId(reviewId, userId)) {
            User voter = users.findById(userId)
                    .orElseThrow(() -> NotFoundException.of("User", userId));
            ReviewHelpful mark = new ReviewHelpful();
            mark.setReview(review);
            mark.setUser(voter);
            helpful.save(mark);
            review.setHelpfulCount(review.getHelpfulCount() + 1);
            reviews.save(review);
        }
        return review.getHelpfulCount();
    }

    /** Recomputes the product's denormalised rating columns from its reviews. */
    @Transactional
    public void recalculate(Long productId) {
        Product product = requireProduct(productId);
        double average = reviews.averageForProduct(productId);
        product.setRatingAvg(BigDecimal.valueOf(average).setScale(1, RoundingMode.HALF_UP));
        product.setReviewCount((int) reviews.countByProductIdAndApprovedTrue(productId));
        products.save(product);
    }

    private ReviewDto toDto(Review review, Long currentUserId) {
        boolean mine = currentUserId != null
                && review.getUser() != null
                && Objects.equals(review.getUser().getId(), currentUserId);
        boolean marked = currentUserId != null
                && helpful.existsByReviewIdAndUserId(review.getId(), currentUserId);

        return new ReviewDto(
                review.getId(),
                review.getAuthorName(),
                review.getCity(),
                review.getRating(),
                review.getTitle(),
                review.getBody(),
                review.isVerified(),
                review.getHelpfulCount(),
                review.getPhotoUrl(),
                mine,
                marked,
                review.getCreatedAt() != null ? ISO.format(review.getCreatedAt()) : null);
    }

    private Product requireProduct(Long productId) {
        return products.findById(productId)
                .orElseThrow(() -> NotFoundException.of("Product", productId));
    }

    private Sort sortFor(String sort) {
        if (sort == null) sort = "recent";
        return switch (sort) {
            case "helpful" -> Sort.by(Sort.Order.desc("helpfulCount"), Sort.Order.desc("id"));
            case "highest" -> Sort.by(Sort.Order.desc("rating"), Sort.Order.desc("id"));
            case "lowest" -> Sort.by(Sort.Order.asc("rating"), Sort.Order.desc("id"));
            default -> Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        };
    }

    /** Used by the seeder so imported reviews produce real aggregates. */
    @Transactional
    public void recalculateAll() {
        List<Long> ids = products.findAll().stream().map(Product::getId).toList();
        ids.forEach(this::recalculate);
    }
}
