package in.mjmasala.api.exception;

import org.springframework.http.HttpStatus;

/** The request is understood but cannot be honoured — bad coupon, empty cart, bad input. */
public class BadRequestException extends ApiException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
