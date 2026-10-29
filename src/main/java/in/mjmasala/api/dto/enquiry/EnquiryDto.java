package in.mjmasala.api.dto.enquiry;

public record EnquiryDto(
        Long id,
        String kind,
        String status,
        String name,
        String email,
        String phone,
        String company,
        String city,
        String monthlyVolume,
        String message,
        String createdAt) {
}
