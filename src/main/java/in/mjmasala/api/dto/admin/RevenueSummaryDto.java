package in.mjmasala.api.dto.admin;

import java.math.BigDecimal;
import java.util.Map;

public record RevenueSummaryDto(
        BigDecimal collected,
        BigDecimal pending,
        BigDecimal refunded,
        Map<String, BigDecimal> byMethod) {
}
