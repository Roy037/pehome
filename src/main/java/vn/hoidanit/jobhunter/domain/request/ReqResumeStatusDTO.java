package vn.hoidanit.jobhunter.domain.request;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import vn.hoidanit.jobhunter.util.constant.ResumeStateEnum;

@Getter
@Setter
public class ReqResumeStatusDTO {
    @NotNull(message = "Vui lòng chọn trạng thái")
    private ResumeStateEnum status;

    // required for INTERVIEW
    private Instant interviewAt;

    @Size(max = 500, message = "Liên kết họp tối đa 500 ký tự")
    @Pattern(regexp = "^(https://\\S+)?$", message = "Liên kết họp phải bắt đầu bằng https://")
    private String meetingLink;

    @Size(max = 1000, message = "Lời nhắn tối đa 1000 ký tự")
    private String decisionNote;

    // e-mail the candidate about this change (default yes)
    private boolean notify = true;
}
