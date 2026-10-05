package vn.hoidanit.jobhunter.controller;

import vn.hoidanit.jobhunter.domain.request.ReqForgotPasswordDTO;
import vn.hoidanit.jobhunter.domain.request.ReqResetPasswordDTO;
import vn.hoidanit.jobhunter.domain.request.ReqVerifyEmailDTO;
import vn.hoidanit.jobhunter.service.PasswordResetService;
import vn.hoidanit.jobhunter.util.error.ConflictException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.domain.request.ReqChangePasswordDTO;
import vn.hoidanit.jobhunter.domain.request.ReqEmployerRegisterDTO;
import vn.hoidanit.jobhunter.domain.request.ReqLoginDTO;
import vn.hoidanit.jobhunter.domain.response.ResCreateUserDTO;
import vn.hoidanit.jobhunter.domain.response.ResLoginDTO;
import vn.hoidanit.jobhunter.service.CompanyService;
import vn.hoidanit.jobhunter.service.EmailVerificationService;
import vn.hoidanit.jobhunter.service.LoginThrottle;
import vn.hoidanit.jobhunter.service.NotificationService;
import vn.hoidanit.jobhunter.service.RoleService;
import vn.hoidanit.jobhunter.service.UserService;
import vn.hoidanit.jobhunter.util.SecurityUtil;
import vn.hoidanit.jobhunter.util.annotation.ApiMessage;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;
import vn.hoidanit.jobhunter.util.error.PermissionException;

@RestController
@RequestMapping("/api/v1")
public class AuthController {

        private final AuthenticationManagerBuilder authenticationManagerBuilder;
        private final SecurityUtil securityUtil;
        private final UserService userService;
        private final RoleService roleService;
        private final CompanyService companyService;
        private final PasswordEncoder passwordEncoder;
        private final PasswordResetService passwordResetService;
        private final LoginThrottle loginThrottle;
        private final NotificationService notificationService;
        private final EmailVerificationService emailVerificationService;

        @Value("${hoidanit.jwt.refresh-token-validity-in-seconds}")
        private long refreshTokenExpiration;

        public AuthController(
                        AuthenticationManagerBuilder authenticationManagerBuilder,
                        SecurityUtil securityUtil,
                        UserService userService,
                        RoleService roleService,
                        CompanyService companyService,
                        PasswordEncoder passwordEncoder,
                        PasswordResetService passwordResetService,
                        LoginThrottle loginThrottle,
                        NotificationService notificationService,
                        EmailVerificationService emailVerificationService) {
                this.emailVerificationService = emailVerificationService;
                this.notificationService = notificationService;
                this.passwordResetService = passwordResetService;
                this.loginThrottle = loginThrottle;
                this.authenticationManagerBuilder = authenticationManagerBuilder;
                this.securityUtil = securityUtil;
                this.userService = userService;
                this.roleService = roleService;
                this.companyService = companyService;
                this.passwordEncoder = passwordEncoder;
        }

        @PostMapping("/auth/login")
        public ResponseEntity<ResLoginDTO> login(@Valid @RequestBody ReqLoginDTO loginDto) throws PermissionException {
                // too many wrong passwords for this account recently: refuse before even checking this one
                this.loginThrottle.check(loginDto.getUsername());

                // Nạp input gồm username/password vào Security
                UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                                loginDto.getUsername(), loginDto.getPassword());

                // xác thực người dùng => cần viết hàm loadUserByUsername
                Authentication authentication;
                try {
                        authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);
                } catch (AuthenticationException e) {
                        this.loginThrottle.failed(loginDto.getUsername());
                        throw e;
                }
                this.loginThrottle.succeeded(loginDto.getUsername());

                // only someone who knows the password is told the account is locked
                User signingIn = this.userService.handleGetUserByUsername(loginDto.getUsername());
                if (signingIn != null && signingIn.isLocked()) {
                        throw new PermissionException("Tài khoản của bạn đã bị khóa. Vui lòng liên hệ quản trị viên.");
                }

                // set thông tin người dùng đăng nhập vào context (có thể sử dụng sau này)
                SecurityContextHolder.getContext().setAuthentication(authentication);

                ResLoginDTO res = new ResLoginDTO();
                User currentUserDB = this.userService.handleGetUserByUsername(loginDto.getUsername());
                if (currentUserDB != null) {
                        res.setUser(ResLoginDTO.UserLogin.from(currentUserDB));
                }

                // create access token
                String access_token = this.securityUtil.createAccessToken(authentication.getName(), res);
                res.setAccessToken(access_token);

                // create refresh token
                String refresh_token = this.securityUtil.createRefreshToken(loginDto.getUsername(), res);

                // update user
                this.userService.updateUserToken(refresh_token, loginDto.getUsername());

                // set cookies
                ResponseCookie resCookies = ResponseCookie
                                .from("refresh_token", refresh_token)
                                .httpOnly(true)
                                .secure(true)
                                .path("/")
                                .maxAge(refreshTokenExpiration)
                                .build();

                return ResponseEntity.ok()
                                .header(HttpHeaders.SET_COOKIE, resCookies.toString())
                                .body(res);
        }

        @GetMapping("/auth/account")
        @ApiMessage("fetch account")
        public ResponseEntity<ResLoginDTO.UserGetAccount> getAccount() {
                String email = SecurityUtil.getCurrentUserLogin().isPresent()
                                ? SecurityUtil.getCurrentUserLogin().get()
                                : "";

                User currentUserDB = this.userService.handleGetUserByUsername(email);
                ResLoginDTO.UserGetAccount userGetAccount = new ResLoginDTO.UserGetAccount();

                if (currentUserDB != null) {
                        userGetAccount.setUser(ResLoginDTO.UserLogin.from(currentUserDB));
                }

                return ResponseEntity.ok().body(userGetAccount);
        }

        @GetMapping("/auth/refresh")
        @ApiMessage("Get User by refresh token")
        public ResponseEntity<ResLoginDTO> getRefreshToken(
                        @CookieValue(name = "refresh_token", defaultValue = "abc") String refresh_token)
                        throws IdInvalidException {
                if (refresh_token.equals("abc")) {
                        throw new IdInvalidException("Bạn không có refresh token ở cookie");
                }
                // check valid
                Jwt decodedToken;
                try {
                        decodedToken = this.securityUtil.checkValidRefreshToken(refresh_token);
                } catch (JwtException e) {
                        // malformed, forged or expired: the same answer as any other bad refresh token, not a 500
                        throw new IdInvalidException("Refresh Token không hợp lệ");
                }
                String email = decodedToken.getSubject();

                // check user by token + email
                User currentUser = this.userService.getUserByRefreshTokenAndEmail(refresh_token, email);
                if (currentUser == null) {
                        throw new IdInvalidException("Refresh Token không hợp lệ");
                }

                // issue new token/set refresh token as cookies
                ResLoginDTO res = new ResLoginDTO();
                User currentUserDB = this.userService.handleGetUserByUsername(email);
                if (currentUserDB != null) {
                        res.setUser(ResLoginDTO.UserLogin.from(currentUserDB));
                }

                // create access token
                String access_token = this.securityUtil.createAccessToken(email, res);
                res.setAccessToken(access_token);

                // create refresh token
                String new_refresh_token = this.securityUtil.createRefreshToken(email, res);

                // update user
                this.userService.updateUserToken(new_refresh_token, email);

                // set cookies
                ResponseCookie resCookies = ResponseCookie
                                .from("refresh_token", new_refresh_token)
                                .httpOnly(true)
                                .secure(true)
                                .path("/")
                                .maxAge(refreshTokenExpiration)
                                .build();

                return ResponseEntity.ok()
                                .header(HttpHeaders.SET_COOKIE, resCookies.toString())
                                .body(res);
        }

        @PostMapping("/auth/logout")
        @ApiMessage("Logout User")
        public ResponseEntity<Void> logout() throws IdInvalidException {
                String email = SecurityUtil.getCurrentUserLogin().isPresent() ? SecurityUtil.getCurrentUserLogin().get()
                                : "";

                if (email.equals("")) {
                        throw new IdInvalidException("Access Token không hợp lệ");
                }

                // update refresh token = null
                this.userService.updateUserToken(null, email);

                // remove refresh token cookie
                ResponseCookie deleteSpringCookie = ResponseCookie
                                .from("refresh_token", null)
                                .httpOnly(true)
                                .secure(true)
                                .path("/")
                                .maxAge(0)
                                .build();

                return ResponseEntity.ok()
                                .header(HttpHeaders.SET_COOKIE, deleteSpringCookie.toString())
                                .body(null);
        }

        @PostMapping("/auth/register")
        @ApiMessage("Register a new user")
        public ResponseEntity<ResCreateUserDTO> register(@Valid @RequestBody User postManUser)
                        throws IdInvalidException {
                boolean isEmailExist = this.userService.isEmailExist(postManUser.getEmail());
                if (isEmailExist) {
                        throw new ConflictException(
                                        "Email " + postManUser.getEmail() + " đã tồn tại, vui lòng sử dụng email khác.");
                }

                // Public sign-up must never choose its own role or company: always a candidate.
                postManUser.setRole(this.roleService.fetchByName("NORMAL_USER"));
                postManUser.setCompany(null);
                postManUser.setLocked(false);
                // a public sign-up must prove it owns the address, whatever the request body claims
                postManUser.setEmailVerified(false);

                String hashPassword = this.passwordEncoder.encode(postManUser.getPassword());
                postManUser.setPassword(hashPassword);
                User ericUser = this.userService.handleCreateUser(postManUser);
                this.emailVerificationService.sendWelcome(ericUser);
                return ResponseEntity.status(HttpStatus.CREATED)
                                .body(this.userService.convertToResCreateUserDTO(ericUser));
        }

        // Same answer whether or not the address exists, so the form cannot be used to find out who has an account.
        @PostMapping("/auth/forgot-password")
        @ApiMessage("If the e-mail is registered, a reset link has been sent (valid for 15 minutes)")
        public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ReqForgotPasswordDTO req) {
                this.passwordResetService.requestReset(req.getEmail());
                return ResponseEntity.ok().body(null);
        }

        @PostMapping("/auth/reset-password")
        @ApiMessage("Password has been reset")
        public ResponseEntity<Void> resetPassword(@Valid @RequestBody ReqResetPasswordDTO req)
                        throws IdInvalidException {
                this.passwordResetService.resetPassword(req.getToken(), req.getNewPassword());
                return ResponseEntity.ok().body(null);
        }

        // Wrong "current password" answers count against the same 8-per-15-minutes limit as logging in.
        @PostMapping("/auth/change-password")
        @ApiMessage("Change the password of the signed-in account")
        public ResponseEntity<Void> changePassword(@Valid @RequestBody ReqChangePasswordDTO req) throws IdInvalidException {
                User me = this.userService.handleGetCurrentUser();
                String throttleKey = "change-password:" + me.getEmail();
                this.loginThrottle.check(throttleKey);
                if (!this.passwordEncoder.matches(req.getCurrentPassword(), me.getPassword())) {
                        this.loginThrottle.failed(throttleKey);
                        throw new IdInvalidException("Mật khẩu hiện tại không đúng.");
                }
                if (req.getCurrentPassword().equals(req.getNewPassword())) {
                        throw new IdInvalidException("Mật khẩu mới phải khác mật khẩu hiện tại.");
                }
                this.loginThrottle.succeeded(throttleKey);
                this.userService.changePassword(me, this.passwordEncoder.encode(req.getNewPassword()));
                return ResponseEntity.ok().body(null);
        }

        @PostMapping("/auth/verify-email")
        @ApiMessage("Confirm the e-mail address with the link sent at sign-up")
        public ResponseEntity<Void> verifyEmail(@Valid @RequestBody ReqVerifyEmailDTO req) throws IdInvalidException {
                this.emailVerificationService.verify(req.getToken());
                return ResponseEntity.ok().body(null);
        }

        @PostMapping("/auth/resend-verification")
        @ApiMessage("Send the verification e-mail again")
        public ResponseEntity<Void> resendVerification() throws IdInvalidException {
                this.emailVerificationService.resend(this.userService.handleGetCurrentUser());
                return ResponseEntity.ok().body(null);
        }

        @PostMapping("/auth/register-employer")
        @ApiMessage("Register an employer; the company stays pending until an admin approves it")
        public ResponseEntity<ResCreateUserDTO> registerEmployer(@Valid @RequestBody ReqEmployerRegisterDTO req)
                        throws IdInvalidException {
                if (this.userService.isEmailExist(req.getEmail().trim())) {
                        throw new ConflictException("Email " + req.getEmail() + " đã tồn tại, vui lòng sử dụng email khác.");
                }
                if (this.companyService.existsByName(req.getCompanyName())) {
                        throw new ConflictException("Công ty " + req.getCompanyName() + " đã tồn tại trên hệ thống.");
                }
                User employer = this.userService.createEmployer(req, this.passwordEncoder.encode(req.getPassword()));
                this.emailVerificationService.sendEmployerWelcome(employer);
                this.notificationService.adminCompanyPending(employer.getCompany(), false);
                return ResponseEntity.status(HttpStatus.CREATED)
                                .body(this.userService.convertToResCreateUserDTO(employer));
        }
}