package vn.hoidanit.jobhunter.domain.response.payment;

/** outcome is SUCCESS, FAILED or PENDING; the order carries the plan and dates when it succeeded. */
public record ResPaymentResultDTO(String outcome, String message, ResOrderDTO order) {
}
