package vn.hoidanit.jobhunter.domain.request;

import jakarta.validation.constraints.NotBlank;

public record ReqMockPaymentDTO(@NotBlank String txnRef, boolean success) {
}
