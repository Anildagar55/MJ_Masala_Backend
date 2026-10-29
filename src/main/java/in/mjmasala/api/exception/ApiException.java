package in.mjmasala.api.exception;

import org.springframework.http.HttpStatus;

/** Base class for the failures this API reports with a deliberate status and message. */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;

    protected ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
