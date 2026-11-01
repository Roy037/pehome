package vn.hoidanit.jobhunter.domain.response.payment;

public record ResAdminOrderDTO(ResOrderDTO order, String txnRef, String gatewayTxnNo, String bankCode, String responseCode,
        long userId, String userName, String userEmail) {
}
