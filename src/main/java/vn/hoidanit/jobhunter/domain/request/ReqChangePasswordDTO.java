package vn.hoidanit.jobhunter.domain.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReqChangePasswordDTO {
    @NotBlank(message = "Vui lòng nhập mật khẩu hiện tại")
    @Size(max = 100, message = "Mật khẩu hiện tại không đúng")
    private String currentPassword;

    @NotBlank(message = "Vui lòng nhập mật khẩu mới")
    @Size(min = 6, max = 100, message = "Mật khẩu phải từ 6 đến 100 ký tự")
    private String newPassword;
}
