package vn.hoidanit.jobhunter.domain.response.jobreport;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import vn.hoidanit.jobhunter.util.constant.JobReportReasonEnum;

@Getter
@Setter
public class ResJobReportDTO {
    private long id;
    private Instant createdAt;
    private JobReportReasonEnum reason;
    private String detail;
    private UserRef user;
    private JobRef job;

    @Getter
    @Setter
    @AllArgsConstructor
    public static class UserRef {
        private long id;
        private String name;
        private String email;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    public static class JobRef {
        private long id;
        private String name;
        private String companyName;
        private boolean locked;
        private String lockReason;
    }
}
