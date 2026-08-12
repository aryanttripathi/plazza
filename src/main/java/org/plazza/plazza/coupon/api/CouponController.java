package org.plazza.plazza.coupon.api;

import jakarta.validation.Valid;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.StringUtils;
import org.plazza.plazza.common.error.ValidationException;
import org.plazza.plazza.coupon.CouponService;
import org.plazza.plazza.coupon.CouponType;
import org.plazza.plazza.coupon.CreateCouponCommand;
import org.plazza.plazza.coupon.api.dto.CouponResponse;
import org.plazza.plazza.coupon.api.dto.CreateCouponRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/coupons")
public class CouponController {

    private final CouponService couponService;

    public CouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    @PostMapping
    public ResponseEntity<CouponResponse> add(@Valid @RequestBody CreateCouponRequest request) {
        CouponResponse body = CouponResponse.from(couponService.add(new CreateCouponCommand(
                request.code(),
                parseType(request.type()),
                request.value(),
                request.maxDiscount(),
                request.expiresAt())));

        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping
    public List<CouponResponse> list() {
        return couponService.findAll().stream().map(CouponResponse::from).toList();
    }

    @GetMapping("/{code}")
    public CouponResponse get(@PathVariable String code) {
        return CouponResponse.from(couponService.requireByCode(code));
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<Void> delete(@PathVariable String code) {
        couponService.delete(code);
        return ResponseEntity.noContent().build();
    }

    private static CouponType parseType(String raw) {
        CouponType type = EnumUtils.getEnumIgnoreCase(CouponType.class, StringUtils.trimToNull(raw));
        if (type == null) {
            throw new ValidationException("unknown coupon type '" + raw + "', expected one of "
                    + Arrays.toString(CouponType.values()));
        }
        return type;
    }
}
