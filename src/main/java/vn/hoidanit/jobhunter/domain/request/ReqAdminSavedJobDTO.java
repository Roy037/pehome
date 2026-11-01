package vn.hoidanit.jobhunter.domain.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReqAdminSavedJobDTO {
    @NotNull(message = "userId không được để trống")
    private Long userId;

    @NotNull(message = "jobId không được để trống")
    private Long jobId;
}
