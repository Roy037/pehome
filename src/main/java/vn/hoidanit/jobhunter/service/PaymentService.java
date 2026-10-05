package vn.hoidanit.jobhunter.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;

import vn.hoidanit.jobhunter.domain.PlanOrder;
import vn.hoidanit.jobhunter.domain.Subscriber;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.domain.response.ResultPaginationDTO;
import vn.hoidanit.jobhunter.domain.response.payment.ResAdminOrderDTO;
import vn.hoidanit.jobhunter.domain.response.payment.ResCreateOrderDTO;
import vn.hoidanit.jobhunter.domain.response.payment.ResMyPlanDTO;
import vn.hoidanit.jobhunter.domain.response.payment.ResOrderDTO;
import vn.hoidanit.jobhunter.domain.response.payment.ResPaymentResultDTO;
import vn.hoidanit.jobhunter.domain.response.payment.ResPaymentMethodDTO;
import vn.hoidanit.jobhunter.domain.response.payment.ResPlanDTO;
import vn.hoidanit.jobhunter.repository.PlanOrderRepository;
import vn.hoidanit.jobhunter.repository.SavedJobRepository;
import vn.hoidanit.jobhunter.repository.SubscriberRepository;
import vn.hoidanit.jobhunter.util.constant.OrderStatusEnum;
import vn.hoidanit.jobhunter.util.constant.PaymentMethodEnum;
import vn.hoidanit.jobhunter.util.constant.PlanEnum;
import vn.hoidanit.jobhunter.util.error.GatewayNotConfiguredException;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;
import vn.hoidanit.jobhunter.util.error.ResourceNotFoundException;

@Service
public class PaymentService {
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    // VNPay's IPN answer codes
    public static final String OK = "00", NOT_FOUND = "01", ALREADY_CONFIRMED = "02", BAD_AMOUNT = "04", BAD_SIGNATURE = "97";

    private final SecureRandom random = new SecureRandom();
    private final PlanOrderRepository orderRepository;
    private final SubscriberRepository subscriberRepository;
    private final SavedJobRepository savedJobRepository;
    private final PlanService planService;
    private final VnpayService vnpay;
    private final ZalopayService zalopay;
    private final MomoService momo;
    private final TransactionTemplate transactions;
    private final NotificationService notificationService;

    public PaymentService(PlanOrderRepository orderRepository, SubscriberRepository subscriberRepository,
            SavedJobRepository savedJobRepository, PlanService planService, VnpayService vnpay,
            NotificationService notificationService, ZalopayService zalopay, MomoService momo,
            PlatformTransactionManager transactionManager) {
        this.notificationService = notificationService;
        this.orderRepository = orderRepository;
        this.subscriberRepository = subscriberRepository;
        this.savedJobRepository = savedJobRepository;
        this.planService = planService;
        this.vnpay = vnpay;
        this.zalopay = zalopay;
        this.momo = momo;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public List<ResPlanDTO> plans() {
        return Arrays.stream(PlanEnum.values())
                .map(p -> new ResPlanDTO(p.name(), p.getLabel(), p.getPriceVnd(), PlanEnum.DURATION_DAYS, p.getAlertSkills(), p.getSavedJobs(), p.isHighlight()))
                .toList();
    }

    public List<ResPaymentMethodDTO> methods() {
        return Arrays.stream(PaymentMethodEnum.values()).map(m -> new ResPaymentMethodDTO(m.name(), available(m))).toList();
    }

    private boolean available(PaymentMethodEnum method) {
        if (this.vnpay.isMock()) return true;
        return switch (method) {
            case ZALOPAY -> this.zalopay.isConfigured();
            case MOMO -> this.momo.isConfigured();
            default -> method.isViaVnpay() && this.vnpay.isConfigured();
        };
    }

    @Transactional
    public ResCreateOrderDTO createOrder(User user, PlanEnum plan, PaymentMethodEnum method, String clientIp) throws IdInvalidException {
        PaymentMethodEnum chosen = method == null ? PaymentMethodEnum.VNPAY : method;
        boolean realZalopay = chosen == PaymentMethodEnum.ZALOPAY && !this.vnpay.isMock();
        boolean realMomo = chosen == PaymentMethodEnum.MOMO && !this.vnpay.isMock();
        if (!available(chosen)) {
            throw new GatewayNotConfiguredException("Cổng thanh toán chưa được cấu hình. Vui lòng thử lại sau.");
        }
        PlanOrder order = new PlanOrder();
        order.setUser(user);
        order.setPlan(plan);
        order.setMethod(chosen);
        order.setAmount(plan.getPriceVnd());
        order.setStatus(OrderStatusEnum.PENDING);
        String reference = "AT" + System.currentTimeMillis() + String.format("%04d", this.random.nextInt(10_000));
        if (realMomo) reference = "MM" + UUID.randomUUID().toString().replace("-", "");
        order.setTxnRef(realZalopay ? DateTimeFormatter.ofPattern("yyMMdd").format(ZonedDateTime.now(VIETNAM)) + "_" + reference : reference);
        order = this.orderRepository.save(order);
        String url = realZalopay ? this.zalopay.createPaymentUrl(order)
                : realMomo ? this.momo.createPaymentUrl(order) : this.vnpay.buildPaymentUrl(order, normalize(clientIp));
        return new ResCreateOrderDTO(order.getId(), order.getTxnRef(), url, this.vnpay.isMock());
    }

    // VNPay rejects IPv6 loopback; a missing/odd address becomes 127.0.0.1.
    private static String normalize(String ip) {
        return ip == null || ip.isBlank() || ip.contains(":") ? "127.0.0.1" : ip.trim();
    }

    /** What the gateway's answer meant: a VNPay IPN code, plus the order when one was found. */
    public record Confirmation(String code, String message, PlanOrder order) {
    }

    /**
     * Applies a gateway answer (browser return or IPN) to its order. Safe to call any number of times and from both
     * routes at once: the signature and the amount are checked, the order row is locked, and only a PENDING order moves.
     */
    @Transactional
    public Confirmation confirm(Map<String, String> params) {
        if (!this.vnpay.verify(params)) {
            return new Confirmation(BAD_SIGNATURE, "Invalid signature", null);
        }
        PlanOrder order = this.orderRepository.findByTxnRefForUpdate(params.getOrDefault("vnp_TxnRef", "")).orElse(null);
        if (order == null || (!this.vnpay.isMock() && order.getMethod() != null && !order.getMethod().isViaVnpay())) {
            return new Confirmation(NOT_FOUND, "Order not found", null);
        }
        long gatewayAmount;
        try {
            gatewayAmount = Long.parseLong(params.getOrDefault("vnp_Amount", ""));
        } catch (NumberFormatException e) {
            return new Confirmation(BAD_AMOUNT, "Invalid amount", order);
        }
        if (gatewayAmount != order.getAmount() * 100) {
            return new Confirmation(BAD_AMOUNT, "Invalid amount", order);
        }
        if (order.getStatus() != OrderStatusEnum.PENDING) {
            return new Confirmation(ALREADY_CONFIRMED, "Order already confirmed", order);
        }

        boolean paid = "00".equals(params.get("vnp_ResponseCode")) && "00".equals(params.get("vnp_TransactionStatus"));
        order.setResponseCode(cut(params.get("vnp_ResponseCode"), 5));
        order.setGatewayTxnNo(cut(params.get("vnp_TransactionNo"), 40));
        order.setBankCode(cut(params.get("vnp_BankCode"), 20));
        if (paid) {
            activate(order);
        } else {
            order.setStatus(OrderStatusEnum.FAILED);
        }
        this.orderRepository.save(order);
        if (paid) {
            this.notificationService.paymentReceipt(order);
        }
        return new Confirmation(OK, "Confirm Success", order);
    }

    private void activate(PlanOrder order) {
        Instant now = Instant.now();
        Instant latest = this.orderRepository.latestEnd(order.getUser().getId(), order.getPlan());
        Instant start = latest != null && latest.isAfter(now) ? latest : now;
        order.setStatus(OrderStatusEnum.PAID);
        order.setPaidAt(now);
        order.setStartsAt(start);
        order.setEndsAt(start.plus(Duration.ofDays(PlanEnum.DURATION_DAYS)));
    }

    /** A signed ZaloPay callback is authoritative; duplicate callbacks are acknowledged without extending twice. */
    @Transactional
    public boolean confirmZalopay(JsonNode body) {
        JsonNode data = this.zalopay.verifiedCallback(body);
        if (data == null) return false;
        PlanOrder order = this.orderRepository.findByTxnRefForUpdate(data.path("app_trans_id").asText()).orElse(null);
        if (order == null || order.getMethod() != PaymentMethodEnum.ZALOPAY
                || !String.valueOf(order.getUser().getId()).equals(data.path("app_user").asText())
                || !validZalopayPayment(order, data)) return false;
        if (order.getStatus() != OrderStatusEnum.PAID) payZalopay(order, data);
        return true;
    }

    /** URL parameters only identify an order; payment is verified with ZaloPay's authenticated query API. */
    @Transactional
    public ResPaymentResultDTO resultForZalopayReturn(User user, String txnRef) {
        PlanOrder order = this.orderRepository.findByTxnRefForUpdate(txnRef).orElse(null);
        if (order == null || order.getUser().getId() != user.getId() || order.getMethod() != PaymentMethodEnum.ZALOPAY) {
            throw new ResourceNotFoundException("Không tìm thấy đơn ZaloPay của bạn.");
        }
        if (order.getStatus() == OrderStatusEnum.PENDING) {
            JsonNode data = this.zalopay.query(txnRef);
            int code = data.path("return_code").asInt();
            if (code == 1) {
                if (!validZalopayPayment(order, data)) {
                    throw new GatewayNotConfiguredException("Dữ liệu xác nhận ZaloPay không khớp với đơn hàng.");
                }
                payZalopay(order, data);
            } else if (code == 2 && data.path("sub_return_code").asInt() == -54) {
                order.setStatus(OrderStatusEnum.FAILED);
                order.setResponseCode("-54");
                this.orderRepository.save(order);
            } else if (code != 3 && code != 2) {
                throw new GatewayNotConfiguredException("Chưa nhận được kết quả hợp lệ từ ZaloPay. Vui lòng kiểm tra lại.");
            }
        }
        return switch (order.getStatus()) {
            case PAID -> new ResPaymentResultDTO("SUCCESS", "Thanh toán thành công", toOrderDto(order));
            case FAILED -> new ResPaymentResultDTO("FAILED", "Đơn ZaloPay đã hết thời gian thanh toán.", toOrderDto(order));
            default -> new ResPaymentResultDTO("PENDING", "ZaloPay chưa xác nhận thanh toán. Bạn có thể kiểm tra lại đơn này.", toOrderDto(order));
        };
    }

    private static boolean validZalopayPayment(PlanOrder order, JsonNode data) {
        return data.path("amount").isIntegralNumber() && data.path("amount").canConvertToLong()
                && data.path("amount").asLong() == order.getAmount()
                && data.path("zp_trans_id").isIntegralNumber() && data.path("zp_trans_id").canConvertToLong()
                && data.path("zp_trans_id").asLong() > 0;
    }

    private void payZalopay(PlanOrder order, JsonNode data) {
        order.setGatewayTxnNo(data.path("zp_trans_id").asText());
        order.setResponseCode("1");
        order.setBankCode("ZALOPAY");
        activate(order);
        this.orderRepository.save(order);
        this.notificationService.paymentReceipt(order);
    }

    @Transactional
    public boolean confirmMomo(JsonNode body) {
        if (!this.momo.verifyNotification(body)) return false;
        String reference = body.path("orderId").asText();
        if (!reference.equals(body.path("requestId").asText())) return false;
        PlanOrder order = this.orderRepository.findByTxnRefForUpdate(reference).orElse(null);
        if (order == null || order.getMethod() != PaymentMethodEnum.MOMO || !validMomoResult(order, body)) return false;
        applyMomoResult(order, body);
        return true;
    }

    public ResPaymentResultDTO resultForMomoReturn(User user, String txnRef) {
        PlanOrder initial = this.orderRepository.findByTxnRef(txnRef).orElse(null);
        requireMomoOwner(initial, user);
        if (initial.getStatus() != OrderStatusEnum.PENDING) return momoResult(initial);
        // MoMo permits a 30-second query. Do not hold a row lock while waiting: its IPN must answer within 15 seconds.
        JsonNode data = this.momo.query(txnRef);
        return this.transactions.execute(status -> {
            PlanOrder order = this.orderRepository.findByTxnRefForUpdate(txnRef).orElse(null);
            requireMomoOwner(order, user);
            if (!validMomoResult(order, data)) {
                throw new GatewayNotConfiguredException("Dữ liệu xác nhận MoMo không khớp với đơn hàng.");
            }
            applyMomoResult(order, data);
            return momoResult(order);
        });
    }

    private static void requireMomoOwner(PlanOrder order, User user) {
        if (order == null || order.getUser().getId() != user.getId() || order.getMethod() != PaymentMethodEnum.MOMO) {
            throw new ResourceNotFoundException("Không tìm thấy đơn MoMo của bạn.");
        }
    }

    private static boolean validMomoResult(PlanOrder order, JsonNode data) {
        return order.getTxnRef().equals(data.path("orderId").asText())
                && data.path("amount").isIntegralNumber() && data.path("amount").canConvertToLong()
                && data.path("amount").asLong() == order.getAmount()
                && data.path("resultCode").isIntegralNumber() && data.path("resultCode").canConvertToInt()
                && (data.path("resultCode").asInt() != 0 || (data.path("transId").isIntegralNumber()
                        && data.path("transId").canConvertToLong() && data.path("transId").asLong() > 0));
    }

    private void applyMomoResult(PlanOrder order, JsonNode data) {
        if (order.getStatus() == OrderStatusEnum.PAID) return;
        int code = data.path("resultCode").asInt();
        if (code == 0) {
            order.setGatewayTxnNo(data.path("transId").asText());
            order.setBankCode("MOMO");
            order.setResponseCode("0");
            activate(order);
            this.orderRepository.save(order);
            this.notificationService.paymentReceipt(order);
        } else if (MomoService.isFinalFailure(code)) {
            order.setStatus(OrderStatusEnum.FAILED);
            order.setResponseCode(String.valueOf(code));
            this.orderRepository.save(order);
        }
    }

    private ResPaymentResultDTO momoResult(PlanOrder order) {
        return switch (order.getStatus()) {
            case PAID -> new ResPaymentResultDTO("SUCCESS", "Thanh toán thành công", toOrderDto(order));
            case FAILED -> new ResPaymentResultDTO("FAILED", "Giao dịch MoMo đã bị hủy hoặc không thành công.", toOrderDto(order));
            default -> new ResPaymentResultDTO("PENDING", "MoMo chưa xác nhận thanh toán. Bạn có thể kiểm tra lại đơn này.", toOrderDto(order));
        };
    }

    /** The browser return page: turns a confirmation into a message for the user, or a 4xx for a bad request. */
    @Transactional
    public ResPaymentResultDTO resultForReturn(Map<String, String> params) throws IdInvalidException {
        Confirmation confirmation = confirm(params);
        switch (confirmation.code()) {
            case BAD_SIGNATURE -> throw new IdInvalidException("Dữ liệu thanh toán không hợp lệ.");
            case NOT_FOUND -> throw new ResourceNotFoundException("Không tìm thấy đơn hàng.");
            case BAD_AMOUNT -> throw new IdInvalidException("Số tiền thanh toán không khớp với đơn hàng.");
            default -> {
                PlanOrder order = confirmation.order();
                boolean paid = order.getStatus() == OrderStatusEnum.PAID;
                return new ResPaymentResultDTO(paid ? "SUCCESS" : "FAILED",
                        paid ? "Thanh toán thành công" : "Thanh toán chưa hoàn tất. Bạn chưa bị trừ tiền cho đơn này.", toOrderDto(order));
            }
        }
    }

    @Transactional(readOnly = true)
    public ResMyPlanDTO myPlan(User user) {
        PlanEnum plan = this.planService.activePlan(user.getId());
        Subscriber subscription = this.subscriberRepository.findByEmail(user.getEmail());
        int alertsUsed = subscription == null || subscription.getSkills() == null ? 0 : subscription.getSkills().size();
        return new ResMyPlanDTO(plan == null ? null : plan.name(), PlanService.label(plan),
                plan == null ? null : this.planService.activeUntil(user.getId(), plan),
                plan == null ? PlanEnum.FREE_ALERT_SKILLS : plan.getAlertSkills(), alertsUsed,
                plan == null ? PlanEnum.FREE_SAVED_JOBS : plan.getSavedJobs(), (int) this.savedJobRepository.countByUserId(user.getId()));
    }

    public List<ResOrderDTO> myOrders(User user) {
        return this.orderRepository.findTop50ByUserIdOrderByCreatedAtDesc(user.getId()).stream().map(this::toOrderDto).toList();
    }

    public ResultPaginationDTO adminOrders(Specification<PlanOrder> spec, Pageable pageable) {
        Page<PlanOrder> page = this.orderRepository.findAll(spec, pageable);
        ResultPaginationDTO rs = new ResultPaginationDTO();
        ResultPaginationDTO.Meta meta = new ResultPaginationDTO.Meta();
        meta.setPage(pageable.getPageNumber() + 1);
        meta.setPageSize(pageable.getPageSize());
        meta.setPages(page.getTotalPages());
        meta.setTotal(page.getTotalElements());
        rs.setMeta(meta);
        rs.setResult(page.getContent().stream().map(o -> new ResAdminOrderDTO(toOrderDto(o), o.getTxnRef(), o.getGatewayTxnNo(),
                o.getBankCode(), o.getResponseCode(), o.getUser().getId(), o.getUser().getName(), o.getUser().getEmail())).toList());
        return rs;
    }

    // ---------------- dev-only fake gateway (app.payment.mock=true) ----------------

    private PlanOrder mockOrder(String txnRef) {
        if (!this.vnpay.isMock()) {
            throw new ResourceNotFoundException("Not Found");
        }
        return this.orderRepository.findByTxnRef(txnRef).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng."));
    }

    public ResOrderDTO mockOrderInfo(String txnRef) {
        return toOrderDto(mockOrder(txnRef));
    }

    /** The URL the fake gateway page sends the browser back to, signed exactly like VNPay's. */
    public String mockReturnUrl(String txnRef, boolean success) {
        PlanOrder order = mockOrder(txnRef);
        Map<String, String> params = new TreeMap<>();
        params.put("vnp_TmnCode", this.vnpay.tmnCode());
        params.put("vnp_Amount", String.valueOf(order.getAmount() * 100));
        params.put("vnp_BankCode", "NCB");
        params.put("vnp_CardType", "ATM");
        params.put("vnp_OrderInfo", "Thanh toan goi " + order.getPlan().getLabel() + " itjobs");
        params.put("vnp_PayDate", STAMP.format(ZonedDateTime.now(VIETNAM)));
        params.put("vnp_ResponseCode", success ? "00" : "24");
        params.put("vnp_TransactionNo", String.valueOf(10_000_000 + this.random.nextInt(89_999_999)));
        params.put("vnp_TransactionStatus", success ? "00" : "02");
        params.put("vnp_TxnRef", order.getTxnRef());
        return this.vnpay.returnUrl() + "?" + this.vnpay.signedQuery(params);
    }

    private static String cut(String value, int max) {
        return value == null ? null : value.length() > max ? value.substring(0, max) : value;
    }

    private ResOrderDTO toOrderDto(PlanOrder order) {
        OrderStatusEnum status = order.getStatus();
        String shown = status.name();
        if (status == OrderStatusEnum.PENDING && order.getCreatedAt() != null
                && order.getCreatedAt().isBefore(Instant.now().minus(Duration.ofMinutes(VnpayService.PAY_WINDOW_MINUTES)))) {
            shown = "EXPIRED";
        }
        return new ResOrderDTO(order.getId(), order.getPlan().name(), order.getAmount(), shown, order.getCreatedAt(), order.getPaidAt(),
                order.getStartsAt(), order.getEndsAt(), order.getMethod() == null ? null : order.getMethod().name());
    }
}
