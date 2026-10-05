package vn.hoidanit.jobhunter.domain.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReqAdminReviewDTO extends ReqReviewDTO {
    private long id;

    @NotNull(message = "userId không được để trống")
    private Long userId;

    @NotNull(message = "companyId không được để trống")
    private Long companyId;
}
