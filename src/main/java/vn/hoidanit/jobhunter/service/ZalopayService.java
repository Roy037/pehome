package vn.hoidanit.jobhunter.service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import vn.hoidanit.jobhunter.domain.PlanOrder;
import vn.hoidanit.jobhunter.util.error.GatewayNotConfiguredException;

/** ZaloPay v2: Key1 signs requests; Key2 authenticates the original callback data string. */
@Service
public class ZalopayService {
    @Value("${app.payment.zalopay.app-id:0}")
    private int appId;
    @Value("${app.payment.zalopay.key1:}")
    private String key1;
    @Value("${app.payment.zalopay.key2:}")
    private String key2;
    @Value("${app.payment.zalopay.base-url:https://sb-openapi.zalopay.vn/v2}")
    private String baseUrl;
    @Value("${app.payment.zalopay.callback-url:}")
    private String callbackUrl;
    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    private final ObjectMapper mapper;
    private final RestClient client;

    public ZalopayService(ObjectMapper mapper, RestClient.Builder builder) {
        this.mapper = mapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(8000);
        this.client = builder.requestFactory(factory).build();
    }

    public boolean isConfigured() {
        return this.appId > 0 && !this.key1.isBlank() && !this.key2.isBlank();
    }

    public String createPaymentUrl(PlanOrder order) {
        requireConfiguration();
        String redirect = UriComponentsBuilder.fromHttpUrl(this.frontendUrl.replaceAll("/+$", "") + "/")
                .queryParam("payment", "zalopay").queryParam("txnRef", order.getTxnRef()).build().encode().toUriString();
        String embed;
        try {
            embed = this.mapper.writeValueAsString(Map.of("redirecturl", redirect,
                    "preferred_payment_method", List.of("zalopay_wallet")));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize ZaloPay order", e);
        }
        long time = System.currentTimeMillis();
        String user = String.valueOf(order.getUser().getId());
        String item = "[]";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("app_id", this.appId);
        body.put("app_trans_id", order.getTxnRef());
        body.put("app_user", user);
        body.put("app_time", time);
        body.put("amount", order.getAmount()); // ZaloPay takes VND directly, unlike VNPay's amount * 100.
        body.put("description", "itjobs - Thanh toan goi " + order.getPlan().getLabel());
        body.put("expire_duration_seconds", VnpayService.PAY_WINDOW_MINUTES * 60);
        body.put("embed_data", embed);
        body.put("item", item);
        body.put("bank_code", "");
        if (!this.callbackUrl.isBlank()) body.put("callback_url", this.callbackUrl);
        body.put("mac", sign(this.key1, this.appId + "|" + order.getTxnRef() + "|" + user + "|"
                + order.getAmount() + "|" + time + "|" + embed + "|" + item));
        JsonNode result = post("/create", body);
        String url = result.path("order_url").asText();
        if (result.path("return_code").asInt() != 1 || !isGatewayUrl(url)) {
            throw new GatewayNotConfiguredException("ZaloPay chưa tạo được đơn thanh toán. Vui lòng kiểm tra cấu hình sandbox và thử lại.");
        }
        return url;
    }

    public JsonNode query(String txnRef) {
        requireConfiguration();
        return post("/query", Map.of("app_id", this.appId, "app_trans_id", txnRef,
                "mac", sign(this.key1, this.appId + "|" + txnRef + "|" + this.key1)));
    }

    /** Returns null for invalid signatures, malformed JSON, other applications or non-order callbacks. */
    public JsonNode verifiedCallback(JsonNode body) {
        if (!isConfigured() || body == null || body.path("type").asInt() != 1
                || !body.path("data").isTextual() || !body.path("mac").isTextual()) return null;
        String data = body.path("data").asText();
        String signature = body.path("mac").asText();
        if (data.length() > 16_384 || signature.length() != 64) return null;
        try {
            if (!MessageDigest.isEqual(HexFormat.of().parseHex(sign(this.key2, data)),
                    HexFormat.of().parseHex(signature))) return null;
            JsonNode parsed = this.mapper.readTree(data);
            return parsed != null && parsed.path("app_id").asInt() == this.appId ? parsed : null;
        } catch (JsonProcessingException | IllegalArgumentException e) {
            return null;
        }
    }

    private JsonNode post(String path, Map<String, Object> body) {
        try {
            JsonNode result = this.client.post().uri(this.baseUrl.replaceAll("/+$", "") + path)
                    .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
            if (result == null || !result.isObject()) throw new GatewayNotConfiguredException("ZaloPay trả về dữ liệu không hợp lệ.");
            return result;
        } catch (RestClientException e) {
            // Do not expose request bodies/MACs/merchant keys through provider errors.
            throw new GatewayNotConfiguredException("Chưa kết nối được ZaloPay. Vui lòng thử lại sau.");
        }
    }

    private void requireConfiguration() {
        if (!isConfigured()) throw new GatewayNotConfiguredException("ZaloPay sandbox chưa được cấu hình App ID, Key1 và Key2.");
    }

    private static boolean isGatewayUrl(String url) {
        try {
            URI uri = URI.create(url);
            return "https".equals(uri.getScheme()) && uri.getHost() != null
                    && (uri.getHost().equals("zalopay.vn") || uri.getHost().endsWith(".zalopay.vn"));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String sign(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot sign ZaloPay data", e);
        }
    }
}
