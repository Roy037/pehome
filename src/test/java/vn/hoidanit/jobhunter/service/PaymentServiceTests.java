package vn.hoidanit.jobhunter.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import vn.hoidanit.jobhunter.domain.PlanOrder;
import vn.hoidanit.jobhunter.controller.PaymentController;
import vn.hoidanit.jobhunter.util.FormatRestResponse;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.repository.PlanOrderRepository;
import vn.hoidanit.jobhunter.repository.SavedJobRepository;
import vn.hoidanit.jobhunter.repository.SubscriberRepository;
import vn.hoidanit.jobhunter.util.constant.OrderStatusEnum;
import vn.hoidanit.jobhunter.util.constant.PaymentMethodEnum;
import vn.hoidanit.jobhunter.util.constant.PlanEnum;
import vn.hoidanit.jobhunter.util.error.ResourceNotFoundException;

class PaymentServiceTests {
    private final PlanOrderRepository orders = mock(PlanOrderRepository.class);
    private final MomoService momo = mock(MomoService.class);
    private final VnpayService vnpay = mock(VnpayService.class);
    private final ZalopayService zalopay = mock(ZalopayService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private PaymentService payments;
    private PlanOrder order;
    private User user;

    @BeforeEach
    void setup() {
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        payments = new PaymentService(orders, mock(SubscriberRepository.class), mock(SavedJobRepository.class),
                mock(PlanService.class), vnpay, notifications, zalopay, momo, transactions);
        user = new User();
        user.setId(7);
        order = new PlanOrder();
        order.setId(11);
        order.setUser(user);
        order.setPlan(PlanEnum.STANDARD);
        order.setMethod(PaymentMethodEnum.MOMO);
        order.setTxnRef("MM123");
        order.setAmount(99000);
        order.setCreatedAt(Instant.now());
        when(orders.findByTxnRefForUpdate("MM123")).thenReturn(Optional.of(order));
        when(orders.findByTxnRef("MM123")).thenReturn(Optional.of(order));
        when(momo.verifyNotification(any())).thenReturn(true);
    }

    private ObjectNode result(int code) {
        return mapper.createObjectNode().put("orderId", "MM123").put("requestId", "MM123")
                .put("amount", 99000).put("resultCode", code).put("transId", 9876543210L);
    }

    @Test
    void duplicateIpnAndBrowserQueryActivateOnceAndContinueExistingPass() {
        Instant existingEnd = Instant.now().plusSeconds(3600);
        when(orders.latestEnd(7, PlanEnum.STANDARD)).thenReturn(existingEnd);
        assertTrue(payments.confirmMomo(result(0)));
        assertEquals(OrderStatusEnum.PAID, order.getStatus());
        assertEquals(existingEnd, order.getStartsAt());
        assertEquals(existingEnd.plusSeconds(30L * 86400), order.getEndsAt());
        assertTrue(payments.confirmMomo(result(0)));
        assertEquals("SUCCESS", payments.resultForMomoReturn(user, "MM123").outcome());
        assertTrue(payments.confirmMomo(result(1006)), "a late failure must not downgrade a paid order");
        assertEquals(OrderStatusEnum.PAID, order.getStatus());
        verify(notifications, times(1)).paymentReceipt(order);
        verify(momo, never()).query(anyString());
    }

    @Test
    void invalidAmountRequestIdSignatureAndGatewayCannotActivate() {
        assertFalse(payments.confirmMomo(result(0).put("amount", 1)));
        assertFalse(payments.confirmMomo(result(0).put("requestId", "OTHER")));
        assertFalse(payments.confirmMomo(result(0).put("transId", 0)));
        ObjectNode missingCode = result(0);
        missingCode.remove("resultCode");
        assertFalse(payments.confirmMomo(missingCode));
        order.setMethod(PaymentMethodEnum.VNPAY);
        assertFalse(payments.confirmMomo(result(0)));
        order.setMethod(PaymentMethodEnum.MOMO);
        when(momo.verifyNotification(any())).thenReturn(false);
        assertFalse(payments.confirmMomo(result(0)));
        assertEquals(OrderStatusEnum.PENDING, order.getStatus());
        verifyNoInteractions(notifications);
    }

    @Test
    void queryChecksOwnershipAndProcessingIsNotSuccess() {
        User stranger = new User();
        stranger.setId(99);
        assertThrows(ResourceNotFoundException.class, () -> payments.resultForMomoReturn(stranger, "MM123"));
        verify(momo, never()).query(anyString());
        for (int code : new int[] { 1000, 7000, 7002, 9000, 10 }) {
            when(momo.query("MM123")).thenReturn(result(code));
            assertEquals("PENDING", payments.resultForMomoReturn(user, "MM123").outcome());
        }
        when(momo.query("MM123")).thenReturn(result(0));
        assertEquals("SUCCESS", payments.resultForMomoReturn(user, "MM123").outcome());
        verify(notifications, times(1)).paymentReceipt(order);
    }

    @Test
    void cancellationIsFinalAndMomoDoesNotDependOnVnpayCredentials() throws Exception {
        assertTrue(payments.confirmMomo(result(1006)));
        assertEquals("FAILED", payments.resultForMomoReturn(user, "MM123").outcome());
        verifyNoInteractions(notifications);
        when(momo.isConfigured()).thenReturn(true);
        when(orders.save(any())).thenAnswer(call -> call.getArgument(0));
        when(momo.createPaymentUrl(any())).thenReturn("https://test-payment.momo.vn/pay");
        assertEquals("https://test-payment.momo.vn/pay", payments.createOrder(user, PlanEnum.STANDARD, PaymentMethodEnum.MOMO, "127.0.0.1").paymentUrl());
        verify(vnpay, never()).buildPaymentUrl(any(), anyString());
        when(vnpay.verify(any())).thenReturn(true);
        assertEquals(PaymentService.NOT_FOUND, payments.confirm(Map.of("vnp_TxnRef", "MM123", "vnp_Amount", "9900000")).code());
    }

    @Test
    void momoIpnAcknowledgementHasNoResponseEnvelope() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new PaymentController(payments, mock(UserService.class)))
                .setControllerAdvice(new FormatRestResponse()).build();
        mvc.perform(post("/api/v1/payments/momo-ipn").contentType("application/json").content(result(0).toString()))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        when(momo.verifyNotification(any())).thenReturn(false);
        mvc.perform(post("/api/v1/payments/momo-ipn").contentType("application/json").content(result(0).toString()))
                .andExpect(status().isBadRequest());
    }
}
