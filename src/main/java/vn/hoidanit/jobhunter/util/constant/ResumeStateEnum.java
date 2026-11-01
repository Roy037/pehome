package vn.hoidanit.jobhunter.util.constant;

import java.util.EnumSet;
import java.util.Set;

// Application pipeline: PENDING -> REVIEWING -> SHORTLISTED -> INTERVIEW -> ACCEPTED, with REJECTED possible until a
// decision is final. ACCEPTED and REJECTED are end states.
public enum ResumeStateEnum {
    PENDING, REVIEWING, SHORTLISTED, INTERVIEW, ACCEPTED, REJECTED;

    /** The statuses an application in this status may move to. */
    public Set<ResumeStateEnum> next() {
        return switch (this) {
            case PENDING -> EnumSet.of(REVIEWING, SHORTLISTED, REJECTED);
            case REVIEWING -> EnumSet.of(SHORTLISTED, REJECTED);
            case SHORTLISTED -> EnumSet.of(INTERVIEW, REJECTED);
            case INTERVIEW -> EnumSet.of(ACCEPTED, REJECTED);
            case ACCEPTED, REJECTED -> EnumSet.noneOf(ResumeStateEnum.class);
        };
    }
}
