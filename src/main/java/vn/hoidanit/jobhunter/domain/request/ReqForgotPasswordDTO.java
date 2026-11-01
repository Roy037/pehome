package vn.hoidanit.jobhunter.domain.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReqForgotPasswordDTO {
    @NotBlank(message = "Vui lòng nhập email")
    @Email(message = "Email chưa hợp lệ")
    private String email;
}
