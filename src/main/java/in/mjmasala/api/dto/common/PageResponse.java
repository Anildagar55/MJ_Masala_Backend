package in.mjmasala.api.dto.common;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** Envelope for paged endpoints, so the client never parses Spring's internal Page shape. */
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalItems,
        int totalPages,
        boolean last) {

    public static <E, T> PageResponse<T> of(Page<E> source, Function<E, T> mapper) {
        return new PageResponse<>(
                source.getContent().stream().map(mapper).toList(),
                source.getNumber(),
                source.getSize(),
                source.getTotalElements(),
                source.getTotalPages(),
                source.isLast());
    }
}
