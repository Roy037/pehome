package vn.hoidanit.jobhunter.domain.response.payment;

public record ResCreateOrderDTO(long orderId, String txnRef, String paymentUrl, boolean mock) {
}
