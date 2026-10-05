package vn.hoidanit.jobhunter.domain.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReqVerifyEmailDTO {
    @NotBlank(message = "Liên kết xác thực không hợp lệ")
    @Size(max = 200, message = "Liên kết xác thực không hợp lệ")
    private String token;
}
