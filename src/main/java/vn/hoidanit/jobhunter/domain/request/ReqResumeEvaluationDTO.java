package vn.hoidanit.jobhunter.domain.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReqResumeEvaluationDTO {
    @NotNull(message = "Vui lòng nhập điểm đánh giá")
    @Min(value = 1, message = "Điểm từ 1 đến 10")
    @Max(value = 10, message = "Điểm từ 1 đến 10")
    private Integer score;

    @Size(max = 2000, message = "Nhận xét tối đa 2000 ký tự")
    private String remark;
}
