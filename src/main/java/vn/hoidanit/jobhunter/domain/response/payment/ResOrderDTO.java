package vn.hoidanit.jobhunter.domain.response.payment;

import java.time.Instant;

/**
 * status is PENDING, PAID, FAILED, or EXPIRED (a payment that was started but never completed in time). `plan` is set
 * for a candidate plan, `product` for something an employer bought; `label` is the name to show either way.
 */
public record ResOrderDTO(long id, String plan, long amount, String status, Instant createdAt, Instant paidAt,
        Instant startsAt, Instant endsAt, String method, String product, String label) {
}
