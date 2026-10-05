package vn.hoidanit.jobhunter.service;

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;

import vn.hoidanit.jobhunter.domain.PlanOrder;
import vn.hoidanit.jobhunter.util.Hmac;
import vn.hoidanit.jobhunter.util.constant.PlanEnum;
import vn.hoidanit.jobhunter.util.error.GatewayNotConfiguredException;

class MomoServiceTests {
    private final ObjectMapper mapper = new ObjectMapper();

    private MomoService configured() {
        MomoService service = new MomoService(RestClient.builder());
        Map.of("partnerCode", "TESTPARTNER", "accessKey", "test-access", "secretKey", "test-momo-secret",
                "ipnUrl", "https://api.itjobs.test/api/v1/payments/momo-ipn", "frontendUrl", "http://localhost:3000/")
                .forEach((key, value) -> ReflectionTestUtils.setField(service, key, value));
        return service;
    }

    @Test
    void acceptsIndependentSignatureVectorAndRejectsTampering() throws Exception {
        MomoService service = configured();
        ObjectNode callback = (ObjectNode) mapper.readTree("""
                {"partnerCode":"TESTPARTNER","orderId":"MM123","requestId":"MM123","amount":99000,
                 "extraData":"","message":"Successful.","orderInfo":"itjobs Standard","orderType":"momo_wallet",
                 "payType":"qr","responseTime":1700000000000,"resultCode":0,"transId":9876543210,
                 "signature":"676848fac6ec47271bd50af8fb1fe51c3dc53b63b83b5ebe63a832a472bf1c61"}
                """);
        assertTrue(service.verifyNotification(callback));
        assertFalse(service.verifyNotification(callback.deepCopy().put("amount", 1000)));
        assertFalse(service.verifyNotification(callback.deepCopy().put("partnerCode", "OTHER")));
        assertFalse(service.verifyNotification(callback.deepCopy().put("resultCode", 1006)));
        assertFalse(service.verifyNotification(callback.deepCopy().put("signature", "z".repeat(64))));
        ReflectionTestUtils.setField(service, "secretKey", "");
        assertFalse(service.isConfigured());
        assertFalse(service.verifyNotification(callback));
    }

    @Test
    void createAndQueryFollowMomoContractAndRejectMismatchedResponses() throws Exception {
        AtomicReference<JsonNode> request = new AtomicReference<>();
        AtomicReference<String> responseOrder = new AtomicReference<>("MM123");
        AtomicReference<String> payUrl = new AtomicReference<>("https://test-payment.momo.vn/v2/gateway/pay?t=demo");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            JsonNode body = mapper.readTree(exchange.getRequestBody());
            request.set(body);
            ObjectNode response = mapper.createObjectNode().put("partnerCode", "TESTPARTNER")
                    .put("requestId", body.path("requestId").asText()).put("orderId", responseOrder.get())
                    .put("amount", 99000).put("resultCode", 0).put("transId", 9876543210L).put("payUrl", payUrl.get());
            byte[] bytes = mapper.writeValueAsBytes(response);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            MomoService service = configured();
            ReflectionTestUtils.setField(service, "baseUrl", "http://127.0.0.1:" + server.getAddress().getPort());
            PlanOrder order = new PlanOrder();
            order.setTxnRef("MM123");
            order.setPlan(PlanEnum.STANDARD);
            order.setAmount(99000);
            assertEquals(payUrl.get(), service.createPaymentUrl(order));
            JsonNode create = request.get();
            assertEquals("captureWallet", create.path("requestType").asText());
            assertTrue(create.path("autoCapture").asBoolean());
            assertEquals(99000, create.path("amount").asLong());
            assertEquals("http://localhost:3000/?payment=momo&txnRef=MM123", create.path("redirectUrl").asText());
            String raw = "accessKey=test-access&amount=99000&extraData=&ipnUrl=https://api.itjobs.test/api/v1/payments/momo-ipn"
                    + "&orderId=MM123&orderInfo=itjobs - Thanh toan goi Standard&partnerCode=TESTPARTNER"
                    + "&redirectUrl=http://localhost:3000/?payment=momo&txnRef=MM123&requestId=MM123&requestType=captureWallet";
            assertEquals(Hmac.signHex("test-momo-secret".getBytes(StandardCharsets.UTF_8), raw), create.path("signature").asText());
            assertEquals(0, service.query("MM123").path("resultCode").asInt());
            JsonNode query = request.get();
            String queryRaw = "accessKey=test-access&orderId=MM123&partnerCode=TESTPARTNER&requestId=" + query.path("requestId").asText();
            assertEquals(Hmac.signHex("test-momo-secret".getBytes(StandardCharsets.UTF_8), queryRaw), query.path("signature").asText());
            assertNotEquals("MM123", query.path("requestId").asText());
            responseOrder.set("OTHER_ORDER");
            assertThrows(GatewayNotConfiguredException.class, () -> service.query("MM123"));
            responseOrder.set("MM123");
            payUrl.set("https://momo.vn.attacker.test/pay");
            assertThrows(GatewayNotConfiguredException.class, () -> service.createPaymentUrl(order));
        } finally {
            server.stop(0);
        }
    }
}
