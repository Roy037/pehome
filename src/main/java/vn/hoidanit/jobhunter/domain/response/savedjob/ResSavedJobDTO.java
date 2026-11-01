package vn.hoidanit.jobhunter.domain.response.savedjob;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResSavedJobDTO {
    private long id;
    private Instant createdAt;
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
    }
}
