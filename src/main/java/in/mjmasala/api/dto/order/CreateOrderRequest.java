package in.mjmasala.api.dto.order;

import in.mjmasala.api.domain.PaymentMethod;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * The checkout payload. Prices are never accepted from the client — only which SKUs and
 * how many; the server prices the order itself.
 */
public record CreateOrderRequest(

        @NotBlank(message = "Enter the name for delivery")
        @Size(max = 120)
        String contactName,

        @NotBlank(message = "A phone number is required for delivery")
        @Pattern(regexp = "^[6-9][0-9]{9}$", message = "Enter a 10-digit mobile number")
        String contactPhone,

        @NotBlank @Email(message = "That does not look like an email")
        @Size(max = 190)
        String contactEmail,

        @NotBlank(message = "Enter the delivery address")
        @Size(max = 400)
        String shipLine,

        @NotBlank(message = "Enter the city") @Size(max = 80)
        String shipCity,

        @NotBlank(message = "Choose a state") @Size(max = 80)
        String shipState,

        @NotBlank(message = "Enter the pin code")
        @Pattern(regexp = "^[1-9][0-9]{5}$", message = "Enter a 6-digit pin code")
        String shipPin,

        @NotNull(message = "Choose a payment method")
        PaymentMethod paymentMethod,

        String couponCode,

        @Size(max = 1000)
        String notes,

        /** Optional. When omitted the server uses the signed-in customer's saved cart. */
        List<OrderLineRequest> items) {
}
