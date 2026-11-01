package vn.hoidanit.jobhunter.util.constant;

import lombok.Getter;

/**
 * How the customer chose to pay. VNPay's page can open straight on QR, domestic bank or international card (the
 * vnp_BankCode parameter); ZaloPay and MoMo use their own gateways.
 */
@Getter
public enum PaymentMethodEnum {
    MOMO("MoMo", null, false),
    VNPAY("VNPay", null, true),
    VIETQR("VietQR", "VNPAYQR", true),
    ZALOPAY("ZaloPay", null, false),
    BANK("Ngân hàng", "VNBANK", true),
    CARD("Thẻ tín dụng", "INTCARD", true);

    /** Name shown to the customer (receipts). */
    private final String label;
    /** vnp_BankCode that preselects this method on VNPay's page; null leaves the choice to the customer. */
    private final String vnpayBankCode;
    private final boolean viaVnpay;

    PaymentMethodEnum(String label, String vnpayBankCode, boolean viaVnpay) {
        this.label = label;
        this.vnpayBankCode = vnpayBankCode;
        this.viaVnpay = viaVnpay;
    }

    /** VNPay routing; each wallet's credential availability is checked separately in PaymentService. */
    public boolean available(boolean mock) {
        return mock || this.viaVnpay;
    }
}
