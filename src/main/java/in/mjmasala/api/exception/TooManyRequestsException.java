package in.mjmasala.api.exception;

import org.springframework.http.HttpStatus;

/** Rate limit hit — too many OTP requests. Carries 429 so a client can back off. */
public class TooManyRequestsException extends ApiException {

    public TooManyRequestsException(String message) {
        super(HttpStatus.TOO_MANY_REQUESTS, message);
    }
}
