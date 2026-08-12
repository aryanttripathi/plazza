package org.plazza.plazza.ride.api;

import org.plazza.plazza.common.enums.CarType;
import org.plazza.plazza.common.error.ValidationException;
import org.plazza.plazza.common.text.Texts;
import org.plazza.plazza.coupon.CouponService;
import org.plazza.plazza.pricing.DiscountResolver;
import org.plazza.plazza.pricing.FareCalculator;
import org.plazza.plazza.pricing.api.dto.FareResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Arrays;

/**
 * Quotes a fare without booking anything — useful for showing riders a price up front, and the
 * quickest way to demonstrate the pricing engine over HTTP.
 * <p>
 * It lives in the ride module rather than in pricing because it combines pricing with coupons, and
 * pricing must not depend on the coupon module: that dependency would put the "what is a coupon"
 * question inside the component that only knows about kilometres and rates.
 */
@RestController
@RequestMapping("/api/fare")
public class FareController {

    private final FareCalculator fareCalculator;
    private final CouponService coupons;

    public FareController(FareCalculator fareCalculator, CouponService coupons) {
        this.fareCalculator = fareCalculator;
        this.coupons = coupons;
    }

    @GetMapping("/estimate")
    public FareResponse estimate(@RequestParam double distanceKm,
                                 @RequestParam String carType,
                                 @RequestParam(required = false) String couponCode) {

        CarType type = CarType.parseOrNull(carType);
        if (type == null) {
            throw new ValidationException("unknown carType '" + carType + "', expected one of "
                    + Arrays.toString(CarType.values()));
        }

        String code = Texts.normalizeCode(couponCode);
        DiscountResolver discount = code == null
                ? DiscountResolver.none()
                : fareAfterSurge -> {
                    coupons.validate(code);           // an unusable coupon is reported, not ignored
                    return coupons.discountFor(code, fareAfterSurge);
                };

        return FareResponse.from(
                fareCalculator.calculate(distanceKm, type, BigDecimal.ONE, discount));
    }
}
