package in.mjmasala.api.exception;

import org.springframework.http.HttpStatus;

/** The request conflicts with existing state — an email already registered, say. */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
