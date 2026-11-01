package vn.hoidanit.jobhunter.service;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import vn.hoidanit.jobhunter.domain.PlanOrder;
import vn.hoidanit.jobhunter.repository.PlanOrderRepository;
import vn.hoidanit.jobhunter.util.constant.OrderStatusEnum;
import vn.hoidanit.jobhunter.util.constant.PaymentMethodEnum;

/** Recovers missed wallet callbacks, including sandbox payments without a public backend. */
@Component
public class WalletPaymentScheduler {
    private static final Logger log = LoggerFactory.getLogger(WalletPaymentScheduler.class);
    private final PaymentService payments;
    private final ZalopayService zalopay;
    private final VnpayService vnpay;
    private final MomoService momo;
    private final PlanOrderRepository orders;

    public WalletPaymentScheduler(PaymentService payments, ZalopayService zalopay, VnpayService vnpay,
            MomoService momo, PlanOrderRepository orders) {
        this.payments = payments;
        this.zalopay = zalopay;
        this.vnpay = vnpay;
        this.momo = momo;
        this.orders = orders;
    }

    @Scheduled(cron = "${app.payment.zalopay.reconcile-cron:0 * * * * *}")
    public void reconcileZalopay() {
        if (this.vnpay.isMock() || !this.zalopay.isConfigured()) return;
        reconcile(PaymentMethodEnum.ZALOPAY);
    }

    @Scheduled(cron = "${app.payment.momo.reconcile-cron:15 * * * * *}")
    public void reconcileMomo() {
        if (this.vnpay.isMock() || !this.momo.isConfigured()) return;
        reconcile(PaymentMethodEnum.MOMO);
    }

    private void reconcile(PaymentMethodEnum method) {
        Instant now = Instant.now();
        // ponytail: bounded sandbox batch; add pagination/backoff if pending traffic exceeds 20 orders per minute.
        for (PlanOrder order : this.orders.findTop20ByMethodAndStatusAndCreatedAtBetweenOrderByCreatedAtAsc(
                method, OrderStatusEnum.PENDING, now.minus(Duration.ofDays(1)), now.minusSeconds(60))) {
            try {
                if (method == PaymentMethodEnum.MOMO) this.payments.resultForMomoReturn(order.getUser(), order.getTxnRef());
                else this.payments.resultForZalopayReturn(order.getUser(), order.getTxnRef());
            } catch (RuntimeException e) {
                log.warn("{} status query failed for order {}; will retry", method, order.getId());
                break; // One gateway outage must not stall every scheduled job for the whole batch.
            }
        }
    }
}
