package vn.hoidanit.jobhunter.domain.response.profile;

import java.time.Instant;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
import vn.hoidanit.jobhunter.domain.CandidateProfile;
import vn.hoidanit.jobhunter.util.constant.GenderEnum;
import vn.hoidanit.jobhunter.util.constant.LevelEnum;

@Getter
@Setter
public class ResProfileDTO {
    private String name;
    private String email;
    private String avatar;
    private Integer age;
    private GenderEnum gender;
    private String address;
    private String headline;
    private String experience;
    private LevelEnum level;
    private String industry;
    private String occupation;
    private boolean jobAlert;
    private boolean visibleToEmployers;
    private List<String> shortGoals;
    private List<String> longGoals;
    private List<CandidateProfile.Experience> experiences;
    private List<CandidateProfile.Skill> skills;
    private List<CandidateProfile.Reference> references;
    private String cvUrl;
    private String cvName;
    private Instant cvUpdatedAt;
    private Instant updatedAt;
}
