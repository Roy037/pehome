package vn.hoidanit.jobhunter.controller;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.turkraft.springfilter.boot.Filter;
import com.fasterxml.jackson.databind.JsonNode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import vn.hoidanit.jobhunter.domain.PlanOrder;
import vn.hoidanit.jobhunter.domain.request.ReqCreateOrderDTO;
import vn.hoidanit.jobhunter.domain.request.ReqMockPaymentDTO;
import vn.hoidanit.jobhunter.domain.response.ResultPaginationDTO;
import vn.hoidanit.jobhunter.domain.response.payment.ResCreateOrderDTO;
import vn.hoidanit.jobhunter.domain.response.payment.ResMyPlanDTO;
import vn.hoidanit.jobhunter.domain.response.payment.ResOrderDTO;
import vn.hoidanit.jobhunter.domain.response.payment.ResPaymentMethodDTO;
import vn.hoidanit.jobhunter.domain.response.payment.ResPaymentResultDTO;
import vn.hoidanit.jobhunter.domain.response.payment.ResPlanDTO;
import vn.hoidanit.jobhunter.service.PaymentService;
import vn.hoidanit.jobhunter.service.UserService;
import vn.hoidanit.jobhunter.util.annotation.ApiMessage;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;
import vn.hoidanit.jobhunter.util.error.PermissionException;

@RestController
@RequestMapping("/api/v1")
public class PaymentController {

    private final PaymentService paymentService;
    private final UserService userService;

    public PaymentController(PaymentService paymentService, UserService userService) {
        this.paymentService = paymentService;
        this.userService = userService;
    }

    @GetMapping("/plans")
    @ApiMessage("List the premium plans")
    public ResponseEntity<List<ResPlanDTO>> plans() {
        return ResponseEntity.ok(this.paymentService.plans());
    }

    // Public: the picker needs to know which gateways have credentials configured.
    @GetMapping("/payments/methods")
    @ApiMessage("List the payment methods")
    public ResponseEntity<List<ResPaymentMethodDTO>> methods() {
        return ResponseEntity.ok(this.paymentService.methods());
    }

    @GetMapping("/me/plan")
    @ApiMessage("My current plan and what it allows")
    public ResponseEntity<ResMyPlanDTO> myPlan() throws IdInvalidException {
        return ResponseEntity.ok(this.paymentService.myPlan(this.userService.handleGetCurrentUser()));
    }

    @GetMapping("/me/orders")
    @ApiMessage("My orders")
    public ResponseEntity<List<ResOrderDTO>> myOrders() throws IdInvalidException {
        return ResponseEntity.ok(this.paymentService.myOrders(this.userService.handleGetCurrentUser()));
    }

    // Starts a payment with the chosen gateway or the fake gateway in dev.
    @PostMapping("/me/orders")
    @ApiMessage("Create an order for a plan")
    public ResponseEntity<ResCreateOrderDTO> createOrder(@Valid @RequestBody ReqCreateOrderDTO req, HttpServletRequest http)
            throws IdInvalidException, PermissionException {
        String forwarded = http.getHeader("X-Forwarded-For");
        String ip = forwarded != null && !forwarded.isBlank() ? forwarded.split(",")[0] : http.getRemoteAddr();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(this.paymentService.createOrder(this.userService.currentCandidate(), req.plan(), req.method(), ip));
    }

    // Where VNPay sends the browser back to (through the frontend result page). Public: the signature is the credential.
    @GetMapping("/payments/vnpay-return")
    @ApiMessage("Result of a VNPay payment")
    public ResponseEntity<ResPaymentResultDTO> vnpayReturn(@RequestParam Map<String, String> params) throws IdInvalidException {
        return ResponseEntity.ok(this.paymentService.resultForReturn(params));
    }

    // VNPay's server-to-server notification; it expects its own {RspCode, Message} body, not our wrapper.
    @GetMapping(value = "/payments/vnpay-ipn", produces = MediaType.APPLICATION_JSON_VALUE)
    public String vnpayIpn(@RequestParam Map<String, String> params) {
        PaymentService.Confirmation result;
        try {
            result = this.paymentService.confirm(params);
        } catch (RuntimeException e) {
            return "{\"RspCode\":\"99\",\"Message\":\"Unknown error\"}";
        }
        return "{\"RspCode\":\"" + result.code() + "\",\"Message\":\"" + result.message() + "\"}";
    }

    // ZaloPay requires its own JSON response, without the application's response envelope.
    @PostMapping(value = "/payments/zalopay-callback", produces = MediaType.APPLICATION_JSON_VALUE)
    public String zalopayCallback(@RequestBody JsonNode body) {
        try {
            return this.paymentService.confirmZalopay(body)
                    ? "{\"return_code\":1,\"return_message\":\"Success\"}"
                    : "{\"return_code\":2,\"return_message\":\"Invalid\"}";
        } catch (RuntimeException e) {
            return "{\"return_code\":0,\"return_message\":\"Retry later\"}";
        }
    }

    @GetMapping("/me/orders/zalopay-result")
    @ApiMessage("Verified result of my ZaloPay payment")
    public ResponseEntity<ResPaymentResultDTO> zalopayReturn(@RequestParam("txnRef") String txnRef) throws IdInvalidException {
        return ResponseEntity.ok(this.paymentService.resultForZalopayReturn(this.userService.handleGetCurrentUser(), txnRef));
    }

    @PostMapping("/payments/momo-ipn")
    public ResponseEntity<Void> momoIpn(@RequestBody JsonNode body) {
        return this.paymentService.confirmMomo(body) ? ResponseEntity.noContent().build() : ResponseEntity.badRequest().build();
    }

    @GetMapping("/me/orders/momo-result")
    @ApiMessage("Verified result of my MoMo payment")
    public ResponseEntity<ResPaymentResultDTO> momoReturn(@RequestParam("txnRef") String txnRef) throws IdInvalidException {
        return ResponseEntity.ok(this.paymentService.resultForMomoReturn(this.userService.handleGetCurrentUser(), txnRef));
    }

    // ---- fake gateway, only answers when app.payment.mock=true ----

    @GetMapping("/payments/mock/order")
    @ApiMessage("Order shown on the fake gateway page")
    public ResponseEntity<ResOrderDTO> mockOrder(@RequestParam("txnRef") String txnRef) {
        return ResponseEntity.ok(this.paymentService.mockOrderInfo(txnRef));
    }

    @PostMapping("/payments/mock/complete")
    @ApiMessage("Finish a payment on the fake gateway")
    public ResponseEntity<Map<String, String>> mockComplete(@Valid @RequestBody ReqMockPaymentDTO req) {
        return ResponseEntity.ok(Map.of("returnUrl", this.paymentService.mockReturnUrl(req.txnRef(), req.success())));
    }

    @GetMapping("/orders")
    @ApiMessage("Fetch orders with pagination")
    public ResponseEntity<ResultPaginationDTO> adminOrders(@Filter Specification<PlanOrder> spec, Pageable pageable) {
        return ResponseEntity.ok(this.paymentService.adminOrders(spec, pageable));
    }
}
