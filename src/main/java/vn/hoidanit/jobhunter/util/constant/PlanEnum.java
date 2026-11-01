package vn.hoidanit.jobhunter.util.constant;

import lombok.Getter;

/** Candidate premium plans. Each purchase is a one-time pass of {@link #DURATION_DAYS} days; no plan means the free tier. */
@Getter
public enum PlanEnum {
    BASIC("Basic", 49_000, 5, 50, false),
    STANDARD("Standard", 99_000, 10, 100, true),
    PREMIUM("Premium", 149_000, 100, 1_000, true);

    public static final int DURATION_DAYS = 30;
    public static final int FREE_ALERT_SKILLS = 3;
    public static final int FREE_SAVED_JOBS = 20;

    private final String label;
    private final long priceVnd;
    /** How many skills the weekly job-alert e-mail may follow. */
    private final int alertSkills;
    private final int savedJobs;
    /** Employers see a "Premium" mark on this candidate's applications. */
    private final boolean highlight;

    PlanEnum(String label, long priceVnd, int alertSkills, int savedJobs, boolean highlight) {
        this.label = label;
        this.priceVnd = priceVnd;
        this.alertSkills = alertSkills;
        this.savedJobs = savedJobs;
        this.highlight = highlight;
    }
}
