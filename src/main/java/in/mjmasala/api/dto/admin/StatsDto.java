package in.mjmasala.api.dto.admin;

import java.math.BigDecimal;

/** The small dashboard the shop owner actually looks at. */
public record StatsDto(
        long ordersToday,
        long ordersTotal,
        long ordersAwaitingDispatch,
        BigDecimal revenueTotal,
        long productsLive,
        long combosLive,
        long reviewsTotal,
        long enquiriesNew) {
}
