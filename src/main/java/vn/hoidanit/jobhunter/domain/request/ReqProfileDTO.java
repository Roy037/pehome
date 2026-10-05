package vn.hoidanit.jobhunter.domain.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import vn.hoidanit.jobhunter.domain.CandidateProfile;
import vn.hoidanit.jobhunter.util.constant.GenderEnum;
import vn.hoidanit.jobhunter.util.constant.LevelEnum;

@Getter
@Setter
public class ReqProfileDTO {
    @Size(max = 100, message = "Họ tên tối đa 100 ký tự")
    private String name;

    // the account's own details (they live on the user, not the profile); blank / null clears them
    @Min(value = 14, message = "Tuổi phải từ 14 đến 100")
    @Max(value = 100, message = "Tuổi phải từ 14 đến 100")
    private Integer age;

    private GenderEnum gender;

    @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự")
    private String address;

    @Size(max = 120, message = "Chức danh tối đa 120 ký tự")
    private String headline;

    @Pattern(regexp = "^(NONE|LT1|Y1_3|Y3_5|GT5)?$", message = "Kinh nghiệm không hợp lệ")
    private String experience;

    private LevelEnum level;

    @Size(max = 80, message = "Lĩnh vực tối đa 80 ký tự")
    private String industry;

    @Size(max = 80, message = "Ngành nghề tối đa 80 ký tự")
    private String occupation;

    private boolean jobAlert;

    // lets approved employers find this profile (and e-mail / CV) in the talent directory
    private boolean visibleToEmployers;

    // Stored file name returned by POST /files (folder "avatar"); blank removes the photo.
    @Pattern(regexp = "^[A-Za-z0-9._-]*$", message = "Tên file ảnh không hợp lệ")
    @Size(max = 250, message = "Tên file ảnh quá dài")
    private String avatar;

    @Size(max = 10, message = "Tối đa 10 mục tiêu ngắn hạn")
    private List<@Size(max = 300, message = "Mục tiêu tối đa 300 ký tự") String> shortGoals;

    @Size(max = 10, message = "Tối đa 10 mục tiêu dài hạn")
    private List<@Size(max = 300, message = "Mục tiêu tối đa 300 ký tự") String> longGoals;

    @Size(max = 20, message = "Tối đa 20 kinh nghiệm làm việc")
    @Valid
    private List<CandidateProfile.Experience> experiences;

    @Size(max = 30, message = "Tối đa 30 kỹ năng")
    @Valid
    private List<CandidateProfile.Skill> skills;

    @Size(max = 5, message = "Tối đa 5 người tham khảo")
    @Valid
    private List<CandidateProfile.Reference> references;

    // Stored file name returned by POST /files (folder "resume"); blank removes the CV.
    @Pattern(regexp = "^[A-Za-z0-9._-]*$", message = "Tên file CV không hợp lệ")
    @Size(max = 250, message = "Tên file CV quá dài")
    private String cvUrl;

    @Size(max = 200, message = "Tên CV tối đa 200 ký tự")
    private String cvName;
}
