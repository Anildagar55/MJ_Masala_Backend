package in.mjmasala.api.dto.review;

import java.util.List;

public record ReviewPageDto(
        ReviewSummaryDto summary,
        List<ReviewDto> items,
        int page,
        int size,
        long totalItems) {
}
