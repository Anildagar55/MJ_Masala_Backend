package in.mjmasala.api.dto.shipping;

public record PincodeDto(
        String pin,
        boolean serviceable,
        int etaDays,
        boolean codAvailable,
        String message) {
}
