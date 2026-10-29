package in.mjmasala.api.dto.enquiry;

import in.mjmasala.api.domain.EnquiryKind;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateEnquiryRequest(
        EnquiryKind kind,

        @NotBlank(message = "Tell us your name") @Size(max = 120)
        String name,

        @NotBlank @Email(message = "That does not look like an email") @Size(max = 190)
        String email,

        @Size(max = 20)
        String phone,

        @Size(max = 160)
        String company,

        @Size(max = 80)
        String city,

        @Size(max = 60)
        String monthlyVolume,

        @Size(max = 4000)
        String message) {
}
