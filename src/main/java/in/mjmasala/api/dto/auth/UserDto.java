package in.mjmasala.api.dto.auth;

/** Never carries the password hash. */
public record UserDto(Long id, String name, String email, String phone, String role) {
}
