package vn.hoidanit.jobhunter.domain.request;

import jakarta.validation.constraints.NotNull;
import vn.hoidanit.jobhunter.util.constant.EmployerProductEnum;
import vn.hoidanit.jobhunter.util.constant.PaymentMethodEnum;

/** `jobId` is needed for a pin only. */
public record ReqCreateEmployerOrderDTO(@NotNull EmployerProductEnum product, Long jobId, PaymentMethodEnum method) {
}
