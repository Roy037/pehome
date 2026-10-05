package vn.hoidanit.jobhunter.domain.response.payment;

import java.time.Instant;

/** status is PENDING, PAID, FAILED, or EXPIRED (a payment that was started but never completed in time). */
public record ResOrderDTO(long id, String plan, long amount, String status, Instant createdAt, Instant paidAt,
        Instant startsAt, Instant endsAt, String method) {
}
