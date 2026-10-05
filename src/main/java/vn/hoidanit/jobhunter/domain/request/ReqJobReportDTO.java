package vn.hoidanit.jobhunter.domain.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import vn.hoidanit.jobhunter.util.constant.JobReportReasonEnum;

@Getter
@Setter
public class ReqJobReportDTO {
    @NotNull(message = "jobId không được để trống")
    private Long jobId;

    @NotNull(message = "Vui lòng chọn lý do báo cáo")
    private JobReportReasonEnum reason;

    @Size(max = 1000, message = "Chi tiết tối đa 1000 ký tự")
    private String detail;
}
