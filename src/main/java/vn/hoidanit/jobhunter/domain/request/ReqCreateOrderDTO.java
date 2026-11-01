package vn.hoidanit.jobhunter.domain.request;

import jakarta.validation.constraints.NotNull;
import vn.hoidanit.jobhunter.util.constant.PaymentMethodEnum;
import vn.hoidanit.jobhunter.util.constant.PlanEnum;

// method is optional so older clients keep working: it defaults to VNPay
public record ReqCreateOrderDTO(@NotNull(message = "Vui lòng chọn gói") PlanEnum plan, PaymentMethodEnum method) {
}
