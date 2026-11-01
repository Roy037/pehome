package vn.hoidanit.jobhunter.domain.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReqAvatarDTO {
    // Stored file name returned by POST /files (folder "avatar"); blank removes the photo.
    @Pattern(regexp = "^[A-Za-z0-9._-]*$", message = "Tên file ảnh không hợp lệ")
    @Size(max = 250, message = "Tên file ảnh quá dài")
    private String avatar;
}
