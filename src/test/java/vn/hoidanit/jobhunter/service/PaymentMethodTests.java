package vn.hoidanit.jobhunter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import vn.hoidanit.jobhunter.util.constant.PaymentMethodEnum;

class PaymentMethodTests {
    @Test
    void theRealGatewayOnlyOffersWhatVnpayCanDo() {
        List<PaymentMethodEnum> live = Arrays.stream(PaymentMethodEnum.values()).filter(m -> m.available(false)).toList();
        assertEquals(List.of(PaymentMethodEnum.VNPAY, PaymentMethodEnum.VIETQR, PaymentMethodEnum.BANK, PaymentMethodEnum.CARD), live);
        assertFalse(PaymentMethodEnum.MOMO.available(false));
        assertFalse(PaymentMethodEnum.ZALOPAY.available(false));
    }

    @Test
    void theFakeGatewayAcceptsEveryMethod() {
        for (PaymentMethodEnum method : PaymentMethodEnum.values()) {
            assertTrue(method.available(true), method.name());
        }
    }
}
