package in.mjmasala.api.dto.review;

import java.math.BigDecimal;
import java.util.Map;

/**
 * The rating block at the top of the reviews section: average, total, and how many
 * reviews sit at each star (keyed 1–5, always all five present so the bars render).
 */
public record ReviewSummaryDto(
        BigDecimal average,
        int total,
        Map<Integer, Integer> distribution) {
}
