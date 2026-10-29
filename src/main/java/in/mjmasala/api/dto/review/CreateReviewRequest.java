package in.mjmasala.api.dto.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateReviewRequest(

        @Min(value = 1, message = "Pick a rating from 1 to 5")
        @Max(value = 5, message = "Pick a rating from 1 to 5")
        int rating,

        @Size(max = 160, message = "Keep the headline under 160 characters")
        String title,

        @NotBlank(message = "Tell us a little about the spice")
        @Size(max = 4000, message = "That review is a bit too long")
        String body,

        @Size(max = 80)
        String city,

        @Size(max = 500, message = "Photo link is too long")
        String photoUrl) {
}
