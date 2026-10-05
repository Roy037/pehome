package vn.hoidanit.jobhunter.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import vn.hoidanit.jobhunter.util.constant.EmployerProductEnum;
import vn.hoidanit.jobhunter.util.constant.OrderStatusEnum;
import vn.hoidanit.jobhunter.util.constant.PaymentMethodEnum;
import vn.hoidanit.jobhunter.util.constant.PlanEnum;

@Entity
@Table(name = "orders")
@Getter
@Setter
public class PlanOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    /** Set for a candidate plan order, null for an employer product order. */
    @Enumerated(EnumType.STRING)
    private PlanEnum plan;

    /** Set for an employer order (and then `companyId` is the company that bought it, `jobId` the pinned job). */
    @Enumerated(EnumType.STRING)
    private EmployerProductEnum product;
    private Long companyId;
    private Long jobId;

    /** VND, as charged. VNPay receives amount * 100; ZaloPay receives the VND amount directly. */
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatusEnum status = OrderStatusEnum.PENDING;

    @Enumerated(EnumType.STRING)
    private PaymentMethodEnum method;

    @Column(nullable = false, unique = true, length = 40)
    private String txnRef;

    @Column(length = 40)
    private String gatewayTxnNo;
    @Column(length = 20)
    private String bankCode;
    @Column(length = 5)
    private String responseCode;

    private Instant createdAt;
    private Instant paidAt;
    /** The pass is active from startsAt (now, or when a previous pass of the same plan ends) until endsAt. */
    private Instant startsAt;
    private Instant endsAt;

    /** What the gateways print on the payment page, ASCII only: "goi Standard", "ghim tin 7 ngay". */
    public String itemName() {
        return this.product != null ? this.product.getAsciiLabel() : "goi " + this.plan.getLabel();
    }

    /** The name shown to people: "Gói Standard", "Ghim tin 7 ngày". */
    public String itemLabel() {
        return this.product != null ? this.product.getLabel() : "Gói " + this.plan.getLabel();
    }

    /** Where the browser comes back to after paying: employers pay from their own area. */
    public String returnPath() {
        return this.product != null ? "/admin/dich-vu" : "/";
    }

    @PrePersist
    public void handleBeforeCreate() {
        this.createdAt = Instant.now();
    }
}
