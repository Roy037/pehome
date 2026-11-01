package vn.hoidanit.jobhunter.domain.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReqEmployerRegisterDTO {
    @NotBlank(message = "Tên công ty không được để trống")
    @Size(max = 120, message = "Tên công ty tối đa 120 ký tự")
    private String companyName;

    @NotBlank(message = "Địa chỉ công ty không được để trống")
    @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự")
    private String companyAddress;

    @NotBlank(message = "Mã số thuế không được để trống")
    @Pattern(regexp = "^\\d{10}(-\\d{3})?$", message = "Mã số thuế gồm 10 chữ số, hoặc 13 chữ số dạng 0123456789-001")
    private String taxCode;

    @NotBlank(message = "Số điện thoại công ty không được để trống")
    @Pattern(regexp = "^(\\+84|0)\\d{9,10}$", message = "Số điện thoại chưa hợp lệ")
    private String phone;

    @Size(max = 255, message = "Website tối đa 255 ký tự")
    @Pattern(regexp = "^(https?://\\S+)?$", message = "Website cần bắt đầu bằng http:// hoặc https://")
    private String website;

    @NotBlank(message = "Họ tên người liên hệ không được để trống")
    @Size(max = 100, message = "Họ tên tối đa 100 ký tự")
    private String name;

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email chưa hợp lệ")
    private String email;

    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(min = 6, max = 72, message = "Mật khẩu từ 6 đến 72 ký tự")
    private String password;

    @AssertTrue(message = "Bạn cần đồng ý Điều khoản sử dụng dành cho nhà tuyển dụng")
    private boolean acceptTerms;
}
