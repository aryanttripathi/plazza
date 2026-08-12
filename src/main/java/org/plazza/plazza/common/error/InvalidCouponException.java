package org.plazza.plazza.common.error;

import org.springframework.http.HttpStatus;

/** The coupon is unknown, inactive, or past its expiry. */
public class InvalidCouponException extends DomainException {

    public InvalidCouponException(String code, String reason) {
        super("INVALID_COUPON",
              HttpStatus.BAD_REQUEST,
              "coupon " + code + " is invalid: " + reason);
    }
}
