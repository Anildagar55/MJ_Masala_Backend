package in.mjmasala.api.dto.review;

public record ReviewDto(
        Long id,
        String authorName,
        String city,
        int rating,
        String title,
        String body,
        boolean verified,
        int helpfulCount,
        String photoUrl,
        /** True when the caller wrote it, so the UI can offer Delete. */
        boolean mine,
        boolean markedHelpfulByMe,
        String createdAt) {
}
