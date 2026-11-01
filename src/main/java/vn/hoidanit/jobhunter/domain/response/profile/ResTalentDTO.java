package vn.hoidanit.jobhunter.domain.response.profile;

import java.time.Instant;
import java.util.List;

import vn.hoidanit.jobhunter.domain.CandidateProfile;
import vn.hoidanit.jobhunter.util.constant.LevelEnum;

/** What an employer sees of a candidate who opted in. References, goals and contact details are only in the detail view. */
public record ResTalentDTO(long id, String name, String avatar, String headline, String experience, LevelEnum level,
        String industry, String occupation, List<CandidateProfile.Skill> skills, boolean premium, boolean hasCv,
        Instant updatedAt) {

    public record Detail(ResTalentDTO talent, String email, List<String> shortGoals, List<String> longGoals,
            List<CandidateProfile.Experience> experiences, String cvName) {
    }
}
