package vn.hoidanit.jobhunter.service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import vn.hoidanit.jobhunter.domain.Company;
import vn.hoidanit.jobhunter.domain.Job;
import vn.hoidanit.jobhunter.domain.JobReport;
import vn.hoidanit.jobhunter.domain.PlanOrder;
import vn.hoidanit.jobhunter.domain.Review;
import vn.hoidanit.jobhunter.domain.Resume;
import vn.hoidanit.jobhunter.domain.Skill;
import vn.hoidanit.jobhunter.domain.Subscriber;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.oauth.OAuthProvider;
import vn.hoidanit.jobhunter.repository.PlanOrderRepository;
import vn.hoidanit.jobhunter.repository.UserRepository;
import vn.hoidanit.jobhunter.service.EmailService.Notice;
import vn.hoidanit.jobhunter.util.constant.JobReportReasonEnum;

/**
 * Every account, application, company and payment e-mail besides the ATS status ones (ResumeService) and the weekly
 * digest (JobAlertService). Callers hand over saved entities; the text lives here. Sending is async and never fails
 * the caller.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
    /** A pass ending this far ahead gets the renewal reminder (run daily, so each pass falls in one run's window). */
    static final Duration REMIND_AHEAD = Duration.ofDays(3);

    private static final String ADMIN_REASON = "Bạn nhận được email này vì địa chỉ của bạn nằm trong ADMIN_ALERT_EMAIL của hệ thống itjobs.";
    private static final Map<JobReportReasonEnum, String> REPORT_REASONS = Map.of(
            JobReportReasonEnum.SCAM, "Lừa đảo / yêu cầu đóng phí",
            JobReportReasonEnum.MISLEADING, "Thông tin sai lệch",
            JobReportReasonEnum.DUPLICATE, "Tin trùng lặp",
            JobReportReasonEnum.EXPIRED, "Tin đã hết hạn",
            JobReportReasonEnum.INAPPROPRIATE, "Nội dung không phù hợp",
            JobReportReasonEnum.OTHER, "Lý do khác");

    @Value("${app.admin-alert.to:}")
    private String adminTo = "";

    private final EmailService emailService;
    private final UserRepository userRepository;
    private final PlanOrderRepository orderRepository;

    public NotificationService(EmailService emailService, UserRepository userRepository, PlanOrderRepository orderRepository) {
        this.emailService = emailService;
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
    }

    /** Employer accounts of a company that have proven their address (an unproven one may belong to someone else). */
    private List<User> hrOf(Company company) {
        return this.userRepository.findByCompany(company).stream().filter(User::isEmailVerified).toList();
    }

    // ---------- accounts ----------

    /** Sign-up e-mail of a candidate: welcome plus the button that proves the address (valid 48 hours). */
    public void welcome(User user, String verifyPath) {
        this.emailService.sendNotice(user.getEmail(), "Chào mừng bạn đến với itjobs – xác thực email của bạn", Notice.of(
                "Chào mừng bạn đến với itjobs",
                user.getName(),
                "Chỉ còn một bước: bấm nút bên dưới để xác thực email. Sau đó bạn có thể ứng tuyển, nhận việc làm qua email và hoàn thiện hồ sơ để nhà tuyển dụng hiểu bạn hơn. Liên kết có hiệu lực trong 48 giờ.",
                "Xác thực email", verifyPath,
                "Nếu bạn không tạo tài khoản itjobs, hãy bỏ qua email này; tài khoản sẽ không được kích hoạt."));
    }

    /** First sign-in with Google / Facebook / LinkedIn created the account: the provider already proved the address. */
    public void welcomeSocial(User user, OAuthProvider provider) {
        this.emailService.sendNotice(user.getEmail(), "Chào mừng bạn đến với itjobs", Notice.of(
                "Chào mừng bạn đến với itjobs",
                user.getName(),
                "Tài khoản của bạn đã sẵn sàng. Bạn đã đăng nhập bằng " + providerName(provider) + ", nên lần sau chỉ cần bấm lại nút đó. Hoàn thiện hồ sơ để nhà tuyển dụng hiểu bạn hơn, rồi bật thông báo việc làm để không bỏ lỡ cơ hội phù hợp.",
                "Hoàn thiện hồ sơ", "/ho-so",
                "Bạn nhận được email này vì vừa tạo tài khoản trên itjobs. Muốn đăng nhập bằng mật khẩu, hãy dùng “Quên mật khẩu” để tạo mật khẩu."));
    }

    private static String providerName(OAuthProvider provider) {
        return switch (provider) {
            case GOOGLE -> "Google";
            case FACEBOOK -> "Facebook";
            case LINKEDIN -> "LinkedIn";
        };
    }

    /** "Send it again" from the banner. */
    public void verifyEmail(User user, String verifyPath) {
        this.emailService.sendNotice(user.getEmail(), "Xác thực email itjobs của bạn", Notice.of(
                "Xác thực email của bạn",
                user.getName(),
                "Bấm nút bên dưới để xác nhận bạn là chủ của địa chỉ email này. Liên kết có hiệu lực trong 48 giờ và liên kết cũ không còn dùng được.",
                "Xác thực email", verifyPath,
                "Nếu bạn không yêu cầu email này, hãy bỏ qua; không có gì thay đổi với tài khoản."));
    }

    // Security notice: if the owner did not do this, they should act at once.
    public void passwordChanged(User user) {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Tài khoản", user.getEmail());
        details.put("Thời gian", TIME.format(Instant.now()));
        this.emailService.sendNotice(user.getEmail(), "Mật khẩu itjobs của bạn vừa được thay đổi", Notice.of(
                "Mật khẩu đã được thay đổi",
                user.getName(),
                "Mật khẩu tài khoản itjobs của bạn vừa được đặt lại và mọi phiên đăng nhập cũ đã bị đăng xuất. Nếu không phải bạn thực hiện, hãy đặt lại mật khẩu ngay và kiểm tra hộp thư của bạn.",
                "Đăng nhập", "/login",
                "Đây là email bảo mật tự động gửi khi mật khẩu tài khoản thay đổi.")
                .details(details));
    }

    public void employerRegistered(User employer, String verifyPath) {
        Company company = employer.getCompany();
        this.emailService.sendNotice(employer.getEmail(), "Đã nhận đăng ký nhà tuyển dụng – " + company.getName(), Notice.of(
                "Chúng tôi đã nhận đăng ký của bạn",
                employer.getName(),
                "Cảm ơn bạn đã chọn itjobs. Thông tin công ty " + company.getName()
                        + " đang chờ duyệt; bạn sẽ nhận email ngay khi có kết quả. Hãy xác thực email của bạn bằng nút bên dưới (liên kết có hiệu lực 48 giờ) và hoàn thiện hồ sơ công ty để được duyệt nhanh hơn.",
                "Xác thực email", verifyPath,
                "Nếu bạn không đăng ký tài khoản nhà tuyển dụng trên itjobs, hãy bỏ qua email này."));
    }

    // ---------- admin alerts ----------

    /** Who hears about work waiting for review: ADMIN_ALERT_EMAIL (comma separated). Blank = nobody, never guessed from accounts. */
    private List<String> adminRecipients() {
        return Arrays.stream(this.adminTo.split(",")).map(String::trim).filter(address -> !address.isEmpty()).toList();
    }

    /** A company entered the review queue (new employer, or an edited rejected profile). */
    /** An approved company changed its name, tax code or licence: it stays online, the admin is asked to check. */
    public void adminCompanyChanged(Company company) {
        List<String> to = adminRecipients();
        if (to.isEmpty()) {
            return;
        }
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Công ty", company.getName());
        details.put("Mã số thuế", company.getTaxCode() == null ? "Chưa có" : company.getTaxCode());
        details.put("Giấy phép", company.getLicenseFile() == null ? "Chưa có" : "Đã tải lên");
        Notice notice = Notice.of("Công ty đổi thông tin xác minh", "quản trị viên",
                company.getName() + " vừa đổi tên, mã số thuế hoặc giấy phép kinh doanh. Công ty vẫn đang hiển thị, hãy kiểm tra lại.",
                "Mở danh sách công ty", "/admin/company", ADMIN_REASON).details(details);
        for (String address : to) {
            this.emailService.sendNotice(address, "Công ty đổi thông tin xác minh: " + company.getName(), notice);
        }
    }

    public void adminCompanyPending(Company company, boolean resubmitted) {
        List<String> to = adminRecipients();
        if (to.isEmpty()) {
            return;
        }
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Công ty", company.getName());
        details.put("Địa chỉ", company.getAddress() == null ? "—" : company.getAddress());
        details.put("Mã số thuế", company.getTaxCode() == null ? "Chưa có" : company.getTaxCode());
        details.put("Số điện thoại", company.getPhone() == null ? "Chưa có" : company.getPhone());
        details.put("Liên hệ", this.userRepository.findByCompany(company).stream()
                .map(user -> user.getName() + " (" + user.getEmail() + ")").collect(Collectors.joining(", ")));
        Notice notice = Notice.of(
                resubmitted ? "Công ty đã cập nhật và gửi duyệt lại" : "Có công ty mới chờ duyệt",
                "quản trị viên",
                resubmitted
                        ? company.getName() + " đã chỉnh sửa hồ sơ sau khi bị từ chối và đang chờ bạn duyệt lại."
                        : company.getName() + " vừa đăng ký nhà tuyển dụng và đang chờ bạn duyệt trước khi hiển thị công khai.",
                "Mở danh sách công ty", "/admin/company",
                ADMIN_REASON).details(details);
        for (String address : to) {
            this.emailService.sendNotice(address, (resubmitted ? "Duyệt lại: " : "Công ty chờ duyệt: ") + company.getName(), notice);
        }
    }

    /** A candidate reported a job post. */
    public void adminJobReported(JobReport report) {
        List<String> to = adminRecipients();
        if (to.isEmpty()) {
            return;
        }
        Job job = report.getJob();
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Tin tuyển dụng", job.getName());
        details.put("Công ty", job.getCompany() == null ? "—" : job.getCompany().getName());
        details.put("Lý do", REPORT_REASONS.getOrDefault(report.getReason(), String.valueOf(report.getReason())));
        details.put("Người báo cáo", report.getUser().getEmail());
        Notice notice = Notice.of(
                "Có báo cáo tin tuyển dụng mới",
                "quản trị viên",
                "Một ứng viên vừa báo cáo tin “" + job.getName() + "”. Xem xét và khóa tin nếu vi phạm.",
                "Mở hàng đợi báo cáo", "/admin/job-report",
                ADMIN_REASON).details(details);
        if (report.getDetail() != null) {
            notice = notice.note("Mô tả thêm của người báo cáo", report.getDetail());
        }
        for (String address : to) {
            this.emailService.sendNotice(address, "Báo cáo tin: " + job.getName(), notice);
        }
    }

    // ---------- companies ----------

    public void companyApproved(Company company) {
        for (User hr : hrOf(company)) {
            this.emailService.sendNotice(hr.getEmail(), "Công ty " + company.getName() + " đã được duyệt", Notice.of(
                    "Công ty của bạn đã được duyệt",
                    hr.getName(),
                    company.getName() + " đã hiển thị công khai trên itjobs. Bạn có thể đăng tin tuyển dụng và nhận hồ sơ ứng viên ngay bây giờ.",
                    "Đăng tin tuyển dụng", "/admin/job/upsert",
                    "Bạn nhận được email này vì là nhà tuyển dụng của " + company.getName() + " trên itjobs."));
        }
    }

    public void companyRejected(Company company) {
        for (User hr : hrOf(company)) {
            this.emailService.sendNotice(hr.getEmail(), "Thông tin công ty " + company.getName() + " cần được cập nhật", Notice.of(
                    "Thông tin công ty chưa được duyệt",
                    hr.getName(),
                    "Chúng tôi chưa thể duyệt " + company.getName()
                            + " lúc này. Hãy cập nhật thông tin theo góp ý bên dưới và lưu lại, hồ sơ sẽ được gửi duyệt lần nữa.",
                    "Cập nhật thông tin công ty", "/admin",
                    "Bạn nhận được email này vì là nhà tuyển dụng của " + company.getName() + " trên itjobs.")
                    .note("Góp ý từ itjobs", company.getRejectionReason()));
        }
    }

    // ---------- applications ----------

    /** Receipt for the candidate. */
    public void applicationSent(Resume resume, Job job) {
        Company company = job.getCompany();
        String companyName = company == null ? "nhà tuyển dụng" : company.getName();
        String sentOn = DAY.format(resume.getCreatedAt() == null ? Instant.now() : resume.getCreatedAt());
        String candidate = resume.getUser() == null ? resume.getEmail() : resume.getUser().getName();

        Map<String, String> forCandidate = new LinkedHashMap<>();
        forCandidate.put("Vị trí", job.getName());
        forCandidate.put("Công ty", companyName);
        forCandidate.put("Ngày nộp", sentOn);
        this.emailService.sendNotice(resume.getEmail(), "Đã gửi hồ sơ ứng tuyển – " + job.getName(), Notice.of(
                "Hồ sơ của bạn đã được gửi",
                candidate,
                companyName + " đã nhận hồ sơ của bạn. Bạn sẽ nhận email mỗi khi hồ sơ được chuyển sang bước mới.",
                "Xem tin tuyển dụng", "/job/" + job.getId(),
                "Bạn nhận được email này vì đã ứng tuyển trên itjobs. Theo dõi trạng thái hồ sơ trong mục “Hồ sơ đã ứng tuyển”.")
                .details(forCandidate));
        // The employer side is the daily digest (ReminderService#hrDailyDigest), not one e-mail per application.
    }

    // ---------- newsletter ----------

    public void newsletterJoined(Subscriber subscriber) {
        List<Skill> skills = subscriber.getSkills() == null ? List.of() : subscriber.getSkills();
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Kỹ năng theo dõi", skills.isEmpty() ? "Chưa chọn" : skills.stream().map(Skill::getName).collect(Collectors.joining(", ")));
        details.put("Lịch gửi", "Sáng thứ Hai hằng tuần");
        String search = skills.isEmpty() ? "/job"
                : "/job?skills=" + skills.stream().map(skill -> String.valueOf(skill.getId())).collect(Collectors.joining(","));
        this.emailService.sendNotice(subscriber.getEmail(), "Bạn đã đăng ký nhận việc làm qua email", Notice.of(
                "Đăng ký nhận việc làm thành công",
                subscriber.getName(),
                "Mỗi tuần chúng tôi sẽ gửi bạn những tin tuyển dụng mới khớp với kỹ năng đã chọn. Tuần nào không có tin phù hợp, bạn sẽ không nhận email.",
                "Xem việc làm phù hợp", search,
                "Bạn nhận được email này vì đã đăng ký nhận việc làm trên itjobs. Đổi kỹ năng hoặc hủy đăng ký trong mục “Nhận việc làm qua email”.")
                .details(details));
    }

    // ---------- moderation: accounts and posts ----------

    public void accountLocked(User user) {
        this.emailService.sendNotice(user.getEmail(), "Tài khoản itjobs của bạn đã bị tạm khóa", Notice.plain(
                "Tài khoản của bạn đã bị tạm khóa",
                user.getName(),
                "Quản trị viên đã tạm khóa tài khoản này nên bạn chưa thể đăng nhập. Nếu bạn cho rằng đây là nhầm lẫn, hãy trả lời email này hoặc liên hệ bộ phận hỗ trợ itjobs để được xem xét lại.",
                "Đây là email tự động gửi khi trạng thái tài khoản thay đổi."));
    }

    public void accountUnlocked(User user) {
        this.emailService.sendNotice(user.getEmail(), "Tài khoản itjobs của bạn đã được mở khóa", Notice.of(
                "Tài khoản đã được mở khóa",
                user.getName(),
                "Bạn có thể đăng nhập và sử dụng itjobs như bình thường.",
                "Đăng nhập", "/login",
                "Đây là email tự động gửi khi trạng thái tài khoản thay đổi."));
    }

    public void jobLocked(Job job) {
        Company company = job.getCompany();
        if (company == null) {
            return;
        }
        for (User hr : hrOf(company)) {
            this.emailService.sendNotice(hr.getEmail(), "Tin tuyển dụng bị khóa: " + job.getName(), Notice.of(
                    "Tin tuyển dụng của bạn đã bị khóa",
                    hr.getName(),
                    "Tin “" + job.getName() + "” đã bị ẩn khỏi trang công khai và không nhận hồ sơ mới. Bạn vẫn chỉnh sửa được nội dung; hãy liên hệ itjobs sau khi xử lý để tin được mở lại.",
                    "Quản lý tin tuyển dụng", "/admin/job",
                    "Bạn nhận được email này vì là nhà tuyển dụng của " + company.getName() + " trên itjobs.")
                    .note("Lý do khóa", job.getLockReason()));
        }
    }

    public void jobUnlocked(Job job) {
        Company company = job.getCompany();
        if (company == null) {
            return;
        }
        for (User hr : hrOf(company)) {
            this.emailService.sendNotice(hr.getEmail(), "Tin tuyển dụng đã được mở khóa: " + job.getName(), Notice.of(
                    "Tin tuyển dụng đã được mở khóa",
                    hr.getName(),
                    "Tin “" + job.getName() + "” đã hiển thị trở lại và nhận hồ sơ như bình thường.",
                    "Xem tin tuyển dụng", "/job/" + job.getId(),
                    "Bạn nhận được email này vì là nhà tuyển dụng của " + company.getName() + " trên itjobs."));
        }
    }

    // ---------- employers ----------

    /** A candidate rated the company for the first time (editing an old review stays quiet). */
    public void reviewReceived(Review review) {
        Company company = review.getCompany();
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Điểm", review.getRating() + " / 5");
        details.put("Người đánh giá", review.getUser().getName());
        for (User hr : hrOf(company)) {
            this.emailService.sendNotice(hr.getEmail(), "Đánh giá mới cho " + company.getName() + " – " + review.getRating() + "/5", Notice.of(
                    "Công ty của bạn có đánh giá mới",
                    hr.getName(),
                    "Một ứng viên vừa đánh giá " + company.getName() + " trên itjobs. Đánh giá hiển thị công khai trên trang công ty.",
                    "Xem đánh giá", "/company/" + company.getId(),
                    "Bạn nhận được email này vì là nhà tuyển dụng của " + company.getName() + " trên itjobs.")
                    .details(details).note("Nội dung", review.getContent()));
        }
    }

    /** Every verified employer account of a company hears about the posts that close in the coming two days. */
    public void jobsExpiring(Company company, List<Job> jobs) {
        Map<String, String> details = new LinkedHashMap<>();
        for (Job job : jobs) {
            details.put(job.getName(), "hết hạn " + DAY.format(job.getEndDate()));
        }
        for (User hr : hrOf(company)) {
            this.emailService.sendNotice(hr.getEmail(), jobs.size() == 1 ? "Tin sắp hết hạn: " + jobs.get(0).getName() : jobs.size() + " tin tuyển dụng sắp hết hạn", Notice.of(
                    "Tin tuyển dụng sắp hết hạn",
                    hr.getName(),
                    "Sau ngày hết hạn, tin sẽ không còn nhận hồ sơ. Gia hạn bằng cách cập nhật ngày kết thúc nếu bạn vẫn đang tuyển.",
                    "Gia hạn tin tuyển dụng", "/admin/job",
                    "Bạn nhận được email này vì là nhà tuyển dụng của " + company.getName() + " trên itjobs.")
                    .details(details));
        }
    }

    /** Morning summary of the applications that arrived in the last 24 hours (one e-mail instead of one per application). */
    public void hrDailyDigest(Company company, List<Resume> resumes) {
        Map<String, Integer> perJob = new LinkedHashMap<>();
        for (Resume resume : resumes) {
            perJob.merge(resume.getJob().getName(), 1, Integer::sum);
        }
        Map<String, String> details = new LinkedHashMap<>();
        perJob.forEach((job, count) -> details.put(job, count + " hồ sơ"));
        for (User hr : hrOf(company)) {
            this.emailService.sendNotice(hr.getEmail(), resumes.size() + " hồ sơ ứng tuyển mới – " + company.getName(), Notice.of(
                    "Hồ sơ ứng tuyển mới trong 24 giờ qua",
                    hr.getName(),
                    company.getName() + " nhận được " + resumes.size() + " hồ sơ mới cho " + perJob.size()
                            + " vị trí. Xem CV và chuyển hồ sơ sang bước tiếp theo trong trang quản lý.",
                    "Xem hồ sơ ứng tuyển", "/admin/resume",
                    "Bạn nhận được email tổng hợp này mỗi sáng khi có hồ sơ mới, vì là nhà tuyển dụng của " + company.getName() + " trên itjobs.")
                    .details(details));
        }
    }

    // ---------- candidates ----------

    /** Saved posts that close in the coming two days, one e-mail per candidate. */
    public void savedJobsClosing(User user, List<Job> jobs) {
        this.emailService.sendNotice(user.getEmail(), jobs.size() == 1 ? "Việc làm bạn đã lưu sắp hết hạn: " + jobs.get(0).getName() : jobs.size() + " việc làm bạn đã lưu sắp hết hạn", Notice.of(
                "Việc làm đã lưu sắp hết hạn nộp hồ sơ",
                user.getName(),
                "Các việc làm bạn đã lưu sẽ đóng nhận hồ sơ trong 2 ngày tới. Ứng tuyển ngay để không bỏ lỡ.",
                "Xem việc làm đã lưu", "/job/" + jobs.get(0).getId(),
                "Bạn nhận được email này vì đã lưu các việc làm trên itjobs.")
                .jobs("Sắp hết hạn", this.emailService.jobCards(jobs)));
    }

    /** The day before an interview: to the candidate and to the employer accounts of the company. */
    public void interviewTomorrow(Resume resume) {
        Job job = resume.getJob();
        Company company = job.getCompany();
        String when = TIME.format(resume.getInterviewAt());
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Vị trí", job.getName());
        details.put("Công ty", company.getName());
        details.put("Thời gian", when);
        String link = resume.getMeetingLink();

        this.emailService.sendNotice(resume.getEmail(), "Nhắc lịch phỏng vấn ngày mai – " + job.getName(), Notice.of(
                "Ngày mai bạn có lịch phỏng vấn",
                resume.getUser() == null ? null : resume.getUser().getName(),
                "Chúc bạn phỏng vấn thật tốt! Hãy vào phòng họp đúng giờ và kiểm tra micro, camera trước đó.",
                "Vào phòng họp", link,
                "Bạn nhận được email này vì đã ứng tuyển và được mời phỏng vấn trên itjobs.")
                .details(details));
        details.put("Ứng viên", resume.getUser() == null ? resume.getEmail() : resume.getUser().getName());
        for (User hr : hrOf(company)) {
            this.emailService.sendNotice(hr.getEmail(), "Nhắc lịch phỏng vấn ngày mai – " + job.getName(), Notice.of(
                    "Ngày mai có lịch phỏng vấn ứng viên",
                    hr.getName(),
                    "Nhắc bạn lịch phỏng vấn đã hẹn với ứng viên bên dưới.",
                    "Vào phòng họp", link,
                    "Bạn nhận được email này vì là nhà tuyển dụng của " + company.getName() + " trên itjobs.")
                    .details(details));
        }
    }

    // ---------- payments ----------

    public void paymentReceipt(PlanOrder order) {
        User user = order.getUser();
        Map<String, String> details = new LinkedHashMap<>();
        details.put("Gói", order.getPlan().getLabel());
        details.put("Số tiền", money(order.getAmount()));
        if (order.getMethod() != null) {
            details.put("Phương thức", order.getMethod().getLabel());
        }
        details.put("Mã đơn hàng", order.getTxnRef());
        details.put("Hiệu lực", DAY.format(order.getStartsAt()) + " – " + DAY.format(order.getEndsAt()));
        this.emailService.sendNotice(user.getEmail(), "Thanh toán thành công – Gói " + order.getPlan().getLabel(), Notice.of(
                "Thanh toán thành công",
                user.getName(),
                "Cảm ơn bạn đã nâng cấp. Gói " + order.getPlan().getLabel()
                        + " đã được kích hoạt và dùng trong 30 ngày, không tự động gia hạn. Email này là biên nhận cho giao dịch của bạn.",
                "Tìm việc ngay", "/job",
                "Bạn nhận được email này vì đã thanh toán gói Premium trên itjobs.")
                .details(details));
    }

    // Daily 09:00 Vietnam time; set app.plan-reminder.cron=- (PLAN_REMINDER_CRON=-) to switch it off.
    @Scheduled(cron = "${app.plan-reminder.cron:0 0 9 * * *}", zone = "Asia/Ho_Chi_Minh")
    public void remindExpiringPlansDaily() {
        log.info("Plan renewal reminders: {} e-mail(s) dispatched", remindExpiringPlans(Instant.now()));
    }

    /** Reminds users whose last paid pass ends between now+3 days and now+4 days. @return how many were e-mailed */
    public int remindExpiringPlans(Instant now) {
        Instant from = now.plus(REMIND_AHEAD);
        int sent = 0;
        for (PlanOrder order : this.orderRepository.findPaidEndingBetween(from, from.plus(Duration.ofDays(1)))) {
            User user = order.getUser();
            // a later pass (renewed already, or another plan) means nothing is about to run out
            if (!order.getEndsAt().equals(this.orderRepository.latestEndAnyPlan(user.getId()))) {
                continue;
            }
            Map<String, String> details = new LinkedHashMap<>();
            details.put("Gói hiện tại", order.getPlan().getLabel());
            details.put("Hết hạn", DAY.format(order.getEndsAt()));
            this.emailService.sendNotice(user.getEmail(), "Gói " + order.getPlan().getLabel() + " của bạn sắp hết hạn", Notice.of(
                    "Gói Premium sắp hết hạn",
                    user.getName(),
                    "Gói " + order.getPlan().getLabel() + " của bạn sẽ hết hạn vào " + DAY.format(order.getEndsAt())
                            + ". Gia hạn trước ngày này để giữ hạn mức kỹ năng nhận việc qua email, việc làm đã lưu và huy hiệu Premium.",
                    "Gia hạn ngay", "/?goi=1",
                    "Bạn nhận được email này vì đang dùng gói Premium trên itjobs.")
                    .details(details));
            sent++;
        }
        return sent;
    }

    static String money(long amount) {
        return String.format(Locale.ROOT, "%,d", amount).replace(',', '.') + "đ";
    }
}
