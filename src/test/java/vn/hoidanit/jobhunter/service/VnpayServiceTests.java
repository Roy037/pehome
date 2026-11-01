package vn.hoidanit.jobhunter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import vn.hoidanit.jobhunter.domain.PlanOrder;
import vn.hoidanit.jobhunter.util.constant.PaymentMethodEnum;
import vn.hoidanit.jobhunter.util.constant.PlanEnum;

class VnpayServiceTests {
    private static VnpayService configured(boolean mock) {
        VnpayService service = new VnpayService();
        ReflectionTestUtils.setField(service, "tmnCode", "TESTTMN");
        ReflectionTestUtils.setField(service, "hashSecret", "test-secret-for-signature-checks");
        ReflectionTestUtils.setField(service, "payUrl", "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        ReflectionTestUtils.setField(service, "frontendUrl", "http://localhost:3000");
        ReflectionTestUtils.setField(service, "mock", mock);
        return service;
    }

    private static Map<String, String> parse(String query) {
        Map<String, String> out = new HashMap<>();
        for (String pair : query.substring(query.indexOf('?') + 1).split("&")) {
            String[] kv = pair.split("=", 2);
            out.put(kv[0], URLDecoder.decode(kv.length > 1 ? kv[1] : "", StandardCharsets.US_ASCII));
        }
        return out;
    }

    private static PlanOrder order() {
        PlanOrder order = new PlanOrder();
        order.setPlan(PlanEnum.STANDARD);
        order.setAmount(99_000);
        order.setTxnRef("AT123");
        return order;
    }

    @Test
    void signsTheAmountTimesOneHundredAndVerifiesItsOwnSignature() {
        VnpayService service = configured(false);
        Map<String, String> params = parse(service.buildPaymentUrl(order(), "127.0.0.1"));
        assertEquals("9900000", params.get("vnp_Amount"));
        assertEquals("TESTTMN", params.get("vnp_TmnCode"));
        assertEquals("http://localhost:3000/", params.get("vnp_ReturnUrl"));
        assertEquals(128, params.get(VnpayService.SECURE_HASH).length(), "HMAC-SHA512 in hex");
        assertTrue(service.verify(params));
    }

    @Test
    void theChosenMethodOpensVnpayOnThatTabAndStaysSigned() {
        VnpayService service = configured(false);
        for (var entry : Map.of(PaymentMethodEnum.VIETQR, "VNPAYQR", PaymentMethodEnum.BANK, "VNBANK", PaymentMethodEnum.CARD, "INTCARD").entrySet()) {
            PlanOrder order = order();
            order.setMethod(entry.getKey());
            Map<String, String> params = parse(service.buildPaymentUrl(order, "127.0.0.1"));
            assertEquals(entry.getValue(), params.get("vnp_BankCode"));
            assertTrue(service.verify(params), "the bank code is part of the signature");
            params.put("vnp_BankCode", "NCB");
            assertFalse(service.verify(params), "changing it breaks the signature");
        }
        PlanOrder plain = order();
        plain.setMethod(PaymentMethodEnum.VNPAY);
        assertNull(parse(service.buildPaymentUrl(plain, "127.0.0.1")).get("vnp_BankCode"), "plain VNPay lets the customer choose");
        assertNull(parse(service.buildPaymentUrl(order(), "127.0.0.1")).get("vnp_BankCode"), "older orders have no method");
    }

    @Test
    void rejectsTamperedOrUnsignedParameters() {
        VnpayService service = configured(false);
        Map<String, String> params = parse(service.buildPaymentUrl(order(), "127.0.0.1"));

        Map<String, String> cheaper = new HashMap<>(params);
        cheaper.put("vnp_Amount", "100");
        assertFalse(service.verify(cheaper), "a changed amount");

        Map<String, String> otherOrder = new HashMap<>(params);
        otherOrder.put("vnp_TxnRef", "AT999");
        assertFalse(service.verify(otherOrder), "a changed order reference");

        Map<String, String> unsigned = new HashMap<>(params);
        unsigned.remove(VnpayService.SECURE_HASH);
        assertFalse(service.verify(unsigned));

        Map<String, String> forged = new HashMap<>(params);
        forged.put(VnpayService.SECURE_HASH, "0".repeat(128));
        assertFalse(service.verify(forged));
    }

    @Test
    void aDifferentSecretDoesNotVerify() {
        VnpayService signer = configured(false);
        VnpayService other = configured(false);
        ReflectionTestUtils.setField(other, "hashSecret", "another-secret");
        assertFalse(other.verify(parse(signer.buildPaymentUrl(order(), "127.0.0.1"))));
    }

    @Test
    void rejectsAnotherMerchantEvenIfTheSignatureUsesTheSameSecret() {
        VnpayService signer = configured(false);
        VnpayService receiver = configured(false);
        ReflectionTestUtils.setField(receiver, "tmnCode", "OTHER_MERCHANT");
        assertFalse(receiver.verify(parse(signer.buildPaymentUrl(order(), "127.0.0.1"))));
        ReflectionTestUtils.setField(signer, "frontendUrl", "http://localhost:3000/");
        assertEquals("http://localhost:3000/", signer.returnUrl());
    }

    @Test
    void withoutCredentialsNothingVerifiesAndMockHasNoRedirect() {
        VnpayService unconfigured = new VnpayService();
        ReflectionTestUtils.setField(unconfigured, "tmnCode", "");
        ReflectionTestUtils.setField(unconfigured, "hashSecret", "");
        assertFalse(unconfigured.isConfigured());
        assertFalse(unconfigured.verify(Map.of(VnpayService.SECURE_HASH, "abc", "vnp_Amount", "1")));

        VnpayService mock = configured(true);
        assertTrue(mock.isConfigured());
        assertNull(mock.buildPaymentUrl(order(), "127.0.0.1"));
    }
}
