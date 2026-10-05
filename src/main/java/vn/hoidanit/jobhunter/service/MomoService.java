package vn.hoidanit.jobhunter.service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;

import vn.hoidanit.jobhunter.domain.PlanOrder;
import vn.hoidanit.jobhunter.util.Hmac;
import vn.hoidanit.jobhunter.util.error.GatewayNotConfiguredException;

/** MoMo captureWallet, with automatic capture and server-side status queries. */
@Service
public class MomoService {
    @Value("${app.payment.momo.partner-code:}")
    private String partnerCode;
    @Value("${app.payment.momo.access-key:}")
    private String accessKey;
    @Value("${app.payment.momo.secret-key:}")
    private String secretKey;
    @Value("${app.payment.momo.base-url:https://test-payment.momo.vn/v2/gateway/api}")
    private String baseUrl;
    @Value("${app.payment.momo.ipn-url:http://localhost:8080/api/v1/payments/momo-ipn}")
    private String ipnUrl;
    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    private final RestClient client;

    public MomoService(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(30_000); // MoMo specifies a minimum API response timeout of 30 seconds.
        this.client = builder.requestFactory(factory).build();
    }

    public boolean isConfigured() {
        return !this.partnerCode.isBlank() && !this.accessKey.isBlank() && !this.secretKey.isBlank() && !this.ipnUrl.isBlank();
    }

    public String createPaymentUrl(PlanOrder order) {
        requireConfiguration();
        String redirect = UriComponentsBuilder.fromHttpUrl(this.frontendUrl.replaceAll("/+$", "") + "/")
                .queryParam("payment", "momo").queryParam("txnRef", order.getTxnRef()).build().encode().toUriString();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("partnerCode", this.partnerCode);
        body.put("requestId", order.getTxnRef());
        body.put("orderId", order.getTxnRef());
        body.put("amount", order.getAmount());
        body.put("orderInfo", "itjobs - Thanh toan goi " + order.getPlan().getLabel());
        body.put("redirectUrl", redirect);
        body.put("ipnUrl", this.ipnUrl);
        body.put("requestType", "captureWallet");
        body.put("extraData", "");
        body.put("signature", signature(body));
        body.put("autoCapture", true);
        body.put("lang", "vi");
        JsonNode result = post("/create", body);
        String url = result.path("payUrl").asText();
        if (!matchesRequest(result, order.getTxnRef(), order.getTxnRef())
                || result.path("resultCode").asInt(-1) != 0
                || !result.path("amount").isIntegralNumber() || result.path("amount").asLong() != order.getAmount()
                || !isGatewayUrl(url)) {
            throw new GatewayNotConfiguredException("MoMo chưa tạo được đơn thanh toán (mã "
                    + result.path("resultCode").asInt(-1) + "). Vui lòng kiểm tra cấu hình sandbox và thử lại.");
        }
        return url;
    }

    public JsonNode query(String txnRef) {
        requireConfiguration();
        String requestId = UUID.randomUUID().toString();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("partnerCode", this.partnerCode);
        body.put("requestId", requestId);
        body.put("orderId", txnRef);
        body.put("signature", signature(body));
        body.put("lang", "vi");
        JsonNode result = post("/query", body);
        if (!matchesRequest(result, txnRef, requestId) || !result.path("resultCode").isIntegralNumber()) {
            throw new GatewayNotConfiguredException("MoMo trả về kết quả không khớp với yêu cầu tra cứu.");
        }
        return result;
    }

    public boolean verifyNotification(JsonNode body) {
        if (!isConfigured() || body == null || !this.partnerCode.equals(body.path("partnerCode").asText())) return false;
        String given = body.path("signature").asText();
        if (given.length() != 64) return false;
        Map<String, Object> signed = new TreeMap<>();
        for (String key : new String[] { "amount", "extraData", "message", "orderId", "orderInfo", "orderType",
                "partnerCode", "payType", "requestId", "responseTime", "resultCode", "transId" }) {
            JsonNode value = body.path(key);
            if (value.isMissingNode() && key.equals("extraData")) signed.put(key, "");
            else if (value.isTextual() || value.isIntegralNumber()) signed.put(key, value.asText());
            else return false;
        }
        try {
            return MessageDigest.isEqual(HexFormat.of().parseHex(signature(signed)), HexFormat.of().parseHex(given));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Only documented final payment failures; outages, authorization and processing stay pending. */
    public static boolean isFinalFailure(int code) {
        return switch (code) {
            case 98, 99, 1001, 1002, 1003, 1004, 1005, 1006, 1007, 1017, 1026, 4001, 4002, 4100 -> true;
            default -> false;
        };
    }

    private boolean matchesRequest(JsonNode result, String orderId, String requestId) {
        return this.partnerCode.equals(result.path("partnerCode").asText())
                && orderId.equals(result.path("orderId").asText()) && requestId.equals(result.path("requestId").asText());
    }

    private String signature(Map<String, Object> fields) {
        Map<String, Object> signed = new TreeMap<>(fields);
        signed.put("accessKey", this.accessKey);
        String data = signed.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("&"));
        return Hmac.signHex(this.secretKey.getBytes(StandardCharsets.UTF_8), data);
    }

    private JsonNode post(String path, Map<String, Object> body) {
        try {
            JsonNode result = this.client.post().uri(this.baseUrl.replaceAll("/+$", "") + path)
                    .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
            if (result == null || !result.isObject()) throw new GatewayNotConfiguredException("MoMo trả về dữ liệu không hợp lệ.");
            return result;
        } catch (RestClientException e) {
            throw new GatewayNotConfiguredException("Chưa kết nối được MoMo. Vui lòng thử lại sau.");
        }
    }

    private void requireConfiguration() {
        if (!isConfigured()) throw new GatewayNotConfiguredException("MoMo sandbox chưa được cấu hình Partner Code, Access Key và Secret Key.");
    }

    private static boolean isGatewayUrl(String url) {
        try {
            URI uri = URI.create(url);
            return "https".equals(uri.getScheme()) && uri.getHost() != null && uri.getHost().endsWith(".momo.vn");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
