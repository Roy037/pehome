package vn.hoidanit.jobhunter.domain.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReqLockJobDTO {
    @NotBlank(message = "Vui lòng nhập lý do khóa tin")
    @Size(max = 500, message = "Lý do tối đa 500 ký tự")
    private String reason;
}
