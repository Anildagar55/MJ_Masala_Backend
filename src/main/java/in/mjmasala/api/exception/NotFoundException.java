package in.mjmasala.api.exception;

import org.springframework.http.HttpStatus;

/** Thrown when a requested entity does not exist, or is not visible to the caller. */
public class NotFoundException extends ApiException {

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }

    public static NotFoundException of(String what, Object id) {
        return new NotFoundException(what + " " + id + " was not found");
    }
}
