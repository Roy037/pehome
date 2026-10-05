package vn.hoidanit.jobhunter.service;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import vn.hoidanit.jobhunter.domain.PlanOrder;

/**
 * VNPay (v2.1.0) signing: the parameters are sorted by name, URL-encoded as US-ASCII, joined as k=v&k=v and signed with
 * HMAC-SHA512 using the merchant's hash secret. The same routine builds the payment URL we redirect to and checks the
 * return redirect / IPN call that comes back, so nobody can fake a "paid" answer without the secret.
 *
 * With {@code app.payment.mock=true} (dev only) the redirect goes to our own fake gateway page instead and a built-in
 * secret is used, so the whole flow can be demoed without a VNPay sandbox account.
 */
@Service
public class VnpayService {
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String MOCK_SECRET = "mock-gateway-secret-not-for-production";
    public static final String SECURE_HASH = "vnp_SecureHash";
    public static final int PAY_WINDOW_MINUTES = 15;

    @Value("${app.payment.vnpay.tmn-code:}")
    private String tmnCode;
    @Value("${app.payment.vnpay.hash-secret:}")
    private String hashSecret;
    @Value("${app.payment.vnpay.url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}")
    private String payUrl;
    @Value("${app.payment.mock:false}")
    private boolean mock;
    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    public boolean isMock() {
        return mock;
    }

    public boolean isConfigured() {
        return mock || (!tmnCode.isBlank() && !hashSecret.isBlank());
    }

    // The browser comes back to the site root with the answer in the query string; the frontend shows it in a modal.
    public String returnUrl() {
        return this.frontendUrl.replaceAll("/+$", "") + "/";
    }

    private String secret() {
        return this.mock ? MOCK_SECRET : this.hashSecret;
    }

    public String tmnCode() {
        return this.mock ? "MOCKTMN" : this.tmnCode;
    }

    /** Where to send the browser to pay for this order. */
    public String buildPaymentUrl(PlanOrder order, String clientIp) {
        if (this.mock) {
            return null; // no redirect: the frontend shows its own fake-gateway modal
        }
        ZonedDateTime now = ZonedDateTime.now(VIETNAM);
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", tmnCode());
        params.put("vnp_Amount", String.valueOf(order.getAmount() * 100));
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", order.getTxnRef());
        params.put("vnp_OrderInfo", "Thanh toan goi " + order.getPlan().getLabel() + " itjobs");
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        if (order.getMethod() != null && order.getMethod().getVnpayBankCode() != null) {
            params.put("vnp_BankCode", order.getMethod().getVnpayBankCode()); // opens VNPay straight on QR / bank / card
        }
        params.put("vnp_ReturnUrl", returnUrl());
        params.put("vnp_IpAddr", clientIp);
        params.put("vnp_CreateDate", STAMP.format(now));
        params.put("vnp_ExpireDate", STAMP.format(now.plusMinutes(PAY_WINDOW_MINUTES)));
        return this.payUrl + "?" + signedQuery(params);
    }

    /** k=v&k=v for the (sorted) parameters plus the signature. */
    public String signedQuery(Map<String, String> params) {
        String data = encodedQuery(new TreeMap<>(params));
        return data + "&" + SECURE_HASH + "=" + hmac(data);
    }

    /** True when the vnp_* parameters carry a signature made with our secret. */
    public boolean verify(Map<String, String> params) {
        String given = params.get(SECURE_HASH);
        if (given == null || given.length() != 128 || !isConfigured() || !tmnCode().equals(params.get("vnp_TmnCode"))) {
            return false;
        }
        Map<String, String> signed = new TreeMap<>();
        params.forEach((key, value) -> {
            if (key.startsWith("vnp_") && !key.equals(SECURE_HASH) && !key.equals("vnp_SecureHashType") && value != null && !value.isEmpty()) {
                signed.put(key, value);
            }
        });
        String expected = hmac(encodedQuery(signed));
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), given.toLowerCase().getBytes(StandardCharsets.US_ASCII));
    }

    private static String encodedQuery(Map<String, String> sorted) {
        StringBuilder out = new StringBuilder();
        sorted.forEach((key, value) -> {
            if (value == null || value.isEmpty()) {
                return;
            }
            if (out.length() > 0) {
                out.append('&');
            }
            out.append(key).append('=').append(java.net.URLEncoder.encode(value, StandardCharsets.US_ASCII));
        });
        return out.toString();
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secret().getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }
}
