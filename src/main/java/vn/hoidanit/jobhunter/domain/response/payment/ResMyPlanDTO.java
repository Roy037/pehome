package vn.hoidanit.jobhunter.domain.response.payment;

import java.time.Instant;

/** plan is null on the free tier. */
public record ResMyPlanDTO(String plan, String name, Instant expiresAt, int alertSkillCap, int alertSkillsUsed,
        int savedJobCap, int savedJobsUsed) {
}
