package vn.hoidanit.jobhunter.domain.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.hoidanit.jobhunter.domain.Company;
import vn.hoidanit.jobhunter.domain.Role;
import vn.hoidanit.jobhunter.domain.User;

@Getter
@Setter
public class ResLoginDTO {
    @JsonProperty("access_token")
    private String accessToken;

    private UserLogin user;

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class UserLogin {
        private long id;
        private String email;
        private String name;
        private Role role;
        private CompanyLogin company;
        private String avatar;
        private boolean emailVerified;

        public static UserLogin from(User user) {
            Company c = user.getCompany();
            return new UserLogin(user.getId(), user.getEmail(), user.getName(), user.getRole(),
                    c == null ? null : new CompanyLogin(c.getId(), c.getName(), c.isApproved(), c.getRejectionReason()), user.getAvatar(),
                    user.isEmailVerified());
        }
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CompanyLogin {
        private long id;
        private String name;
        private boolean approved;
        private String rejectionReason;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class UserGetAccount {
        private UserLogin user;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class UserInsideToken {
        private long id;
        private String email;
        private String name;
    }

}