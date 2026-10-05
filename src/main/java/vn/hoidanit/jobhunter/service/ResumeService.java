package vn.hoidanit.jobhunter.service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import vn.hoidanit.jobhunter.domain.request.ReqResumeEvaluationDTO;
import vn.hoidanit.jobhunter.domain.request.ReqResumeStatusDTO;
import vn.hoidanit.jobhunter.util.constant.ResumeStateEnum;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import vn.hoidanit.jobhunter.util.error.ConflictException;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;
import vn.hoidanit.jobhunter.util.error.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import vn.hoidanit.jobhunter.domain.Company;
import vn.hoidanit.jobhunter.domain.Job;
import vn.hoidanit.jobhunter.domain.Resume;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.domain.response.ResultPaginationDTO;
import vn.hoidanit.jobhunter.domain.response.resume.ResCreateResumeDTO;
import vn.hoidanit.jobhunter.domain.response.resume.ResFetchResumeDTO;
import vn.hoidanit.jobhunter.domain.response.resume.ResUpdateResumeDTO;
import vn.hoidanit.jobhunter.repository.JobRepository;
import vn.hoidanit.jobhunter.repository.ResumeRepository;
import vn.hoidanit.jobhunter.repository.UserRepository;
import vn.hoidanit.jobhunter.util.SecurityUtil;
import vn.hoidanit.jobhunter.util.constant.PlanEnum;
import vn.hoidanit.jobhunter.util.error.PermissionException;

@Service
public class ResumeService {
    private final ResumeRepository resumeRepository;
    private final UserRepository userRepository;
    private final JobRepository jobRepository;
    private final UserService userService;
    private final EmailService emailService;
    private final FileService fileService;
    private final PlanService planService;
    private final NotificationService notificationService;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    public ResumeService(
            ResumeRepository resumeRepository,
            UserRepository userRepository,
            JobRepository jobRepository,
            UserService userService,
            EmailService emailService,
            FileService fileService,
            PlanService planService,
            NotificationService notificationService) {
        this.planService = planService;
        this.notificationService = notificationService;
        this.emailService = emailService;
        this.fileService = fileService;
        this.resumeRepository = resumeRepository;
        this.userRepository = userRepository;
        this.jobRepository = jobRepository;
        this.userService = userService;
    }

    // Employers only see and manage applications sent to their own company's jobs.
    public void assertOwned(Resume resume) throws PermissionException {
        Company mine = this.userService.currentUserCompany();
        if (mine != null) {
            Company owner = resume.getJob() == null ? null : resume.getJob().getCompany();
            if (owner == null || owner.getId() != mine.getId()) {
                throw new PermissionException("Hồ sơ này không thuộc công ty của bạn.");
            }
        }
    }

    // A CV can be opened by its applicant, by an employer of the company that owns the job, and by a super admin.
    public boolean canViewDocument(Resume resume, User viewer) {
        if (viewer.getRole() != null && "SUPER_ADMIN".equals(viewer.getRole().getName())) {
            return true;
        }
        if (resume.getUser() != null && resume.getUser().getId() == viewer.getId()) {
            return true;
        }
        Company mine = viewer.getCompany();
        Company owner = resume.getJob() == null ? null : resume.getJob().getCompany();
        return mine != null && owner != null && owner.getId() == mine.getId();
    }

    public Optional<Resume> fetchById(long id) {
        return this.resumeRepository.findById(id);
    }

    public boolean checkResumeExistByUserAndJob(Resume resume) {
        // check user by id
        if (resume.getUser() == null)
            return false;
        Optional<User> userOptional = this.userRepository.findById(resume.getUser().getId());
        if (userOptional.isEmpty())
            return false;

        // check job by id
        if (resume.getJob() == null)
            return false;
        Optional<Job> jobOptional = this.jobRepository.findById(resume.getJob().getId());
        if (jobOptional.isEmpty())
            return false;

        return true;
    }

    private boolean isSuperAdmin() {
        try {
            User me = this.userService.handleGetCurrentUser();
            return me.getRole() != null && "SUPER_ADMIN".equals(me.getRole().getName());
        } catch (IdInvalidException e) {
            return false;
        }
    }

    /**
     * Moves an application along the pipeline (PENDING -> REVIEWING -> SHORTLISTED -> INTERVIEW -> ACCEPTED, or
     * REJECTED while undecided). A SUPER_ADMIN may correct any status; an employer only moves forward, and only for
     * applications to their own company. Optionally e-mails the candidate.
     */
    @Transactional
    public Resume changeStatus(long id, ReqResumeStatusDTO req) throws IdInvalidException, PermissionException {
        Resume resume = this.resumeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resume với id = " + id + " không tồn tại"));
        assertOwned(resume);

        ResumeStateEnum from = resume.getStatus() == null ? ResumeStateEnum.PENDING : resume.getStatus();
        ResumeStateEnum to = req.getStatus();
        boolean reschedule = from == ResumeStateEnum.INTERVIEW && to == ResumeStateEnum.INTERVIEW;
        if (!isSuperAdmin() && !reschedule && !from.next().contains(to)) {
            throw new ConflictException(from == to
                    ? "Hồ sơ đã ở trạng thái này rồi."
                    : "Không thể chuyển hồ sơ từ " + from + " sang " + to + ".");
        }
        if (to == ResumeStateEnum.INTERVIEW) {
            if (req.getInterviewAt() == null || !req.getInterviewAt().isAfter(Instant.now())) {
                throw new IdInvalidException("Vui lòng chọn thời gian phỏng vấn trong tương lai.");
            }
            if (req.getMeetingLink() == null || req.getMeetingLink().isBlank()) {
                throw new IdInvalidException("Vui lòng nhập liên kết họp (https://...).");
            }
            resume.setInterviewAt(req.getInterviewAt());
            resume.setMeetingLink(req.getMeetingLink().trim());
        }
        resume.setStatus(to);
        if (req.getDecisionNote() != null) {
            resume.setDecisionNote(req.getDecisionNote().isBlank() ? null : req.getDecisionNote().trim());
        }
        resume = this.resumeRepository.save(resume);

        if (req.isNotify()) {
            notifyCandidate(resume, reschedule);
        }
        return resume;
    }

    @Transactional
    public Resume evaluate(long id, ReqResumeEvaluationDTO req) throws PermissionException {
        Resume resume = this.resumeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resume với id = " + id + " không tồn tại"));
        assertOwned(resume);
        resume.setScore(req.getScore());
        resume.setRemark(req.getRemark() == null || req.getRemark().isBlank() ? null : req.getRemark().trim());
        return this.resumeRepository.save(resume);
    }

    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("HH:mm 'ngày' dd/MM/yyyy").withZone(VIETNAM);

    // Mail goes out asynchronously and never blocks or fails the status change.
    private void notifyCandidate(Resume resume, boolean reschedule) {
        Job job = resume.getJob();
        String company = job.getCompany() == null ? "nhà tuyển dụng" : job.getCompany().getName();
        Map<String, Object> vars = new HashMap<>();
        vars.put("name", resume.getUser() == null || resume.getUser().getName() == null ? "bạn" : resume.getUser().getName());
        vars.put("jobName", job.getName());
        vars.put("company", company);
        vars.put("jobUrl", this.frontendUrl + "/job/" + job.getId());
        vars.put("note", resume.getDecisionNote());
        String to = resume.getEmail();
        switch (resume.getStatus()) {
            case SHORTLISTED -> {
                vars.put("heading", "Hồ sơ của bạn đã vào danh sách rút gọn");
                vars.put("intro", company + " đã đưa hồ sơ ứng tuyển vị trí " + job.getName() + " của bạn vào danh sách rút gọn (shortlist). Nhà tuyển dụng sẽ sớm liên hệ để thông báo bước tiếp theo.");
                this.emailService.sendTemplate(to, "Hồ sơ của bạn đã vào danh sách rút gọn – " + job.getName(), "application-status", vars);
            }
            case INTERVIEW -> {
                vars.put("when", WHEN.format(resume.getInterviewAt()));
                vars.put("meetingLink", resume.getMeetingLink());
                vars.put("reschedule", reschedule);
                this.emailService.sendTemplate(to, (reschedule ? "Cập nhật lịch phỏng vấn – " : "Thư mời phỏng vấn – ") + job.getName() + " tại " + company, "interview-invitation", vars);
            }
            case ACCEPTED -> {
                vars.put("heading", "Chúc mừng, bạn đã được nhận!");
                vars.put("intro", company + " đã chấp nhận hồ sơ của bạn cho vị trí " + job.getName() + ". Nhà tuyển dụng sẽ liên hệ với bạn về các bước tiếp theo.");
                this.emailService.sendTemplate(to, "Chúc mừng! Bạn đã được nhận – " + job.getName(), "application-status", vars);
            }
            case REJECTED -> {
                vars.put("heading", "Cảm ơn bạn đã ứng tuyển");
                vars.put("intro", "Sau khi xem xét, " + company + " chưa thể tiếp tục với hồ sơ của bạn cho vị trí " + job.getName() + " lần này. Chúng tôi trân trọng thời gian của bạn và chúc bạn sớm tìm được cơ hội phù hợp.");
                // Keep the candidate going: a few open roles needing the same skills, and a search pre-filtered on them.
                vars.put("jobs", similarJobs(job, resume.getUser()));
                vars.put("ctaLabel", "Khám phá việc làm khác");
                vars.put("ctaUrl", this.frontendUrl + "/job" + skillQuery(job));
                this.emailService.sendTemplate(to, "Kết quả ứng tuyển – " + job.getName(), "application-status", vars);
            }
            default -> { /* PENDING / REVIEWING: nothing to tell the candidate yet */ }
        }
    }

    static final int SIMILAR_JOBS = 3;

    private List<Map<String, Object>> similarJobs(Job job, User user) {
        if (job.getSkills() == null || job.getSkills().isEmpty()) {
            return List.of();
        }
        long userId = user == null ? -1 : user.getId();
        return this.emailService.jobCards(this.jobRepository.findSimilarOpenJobs(job.getSkills(), job.getId(), userId,
                Instant.now(), PageRequest.of(0, SIMILAR_JOBS)));
    }

    private static String skillQuery(Job job) {
        if (job.getSkills() == null || job.getSkills().isEmpty()) {
            return "";
        }
        return "?skills=" + job.getSkills().stream().map(skill -> String.valueOf(skill.getId())).collect(Collectors.joining(","));
    }

    public boolean hasApplied(long userId, long jobId) {
        return this.resumeRepository.existsByUserIdAndJobId(userId, jobId);
    }

    // Cloud links we accept as a CV (always https, no credentials in the address).
    static final List<String> CLOUD_HOSTS = List.of("drive.google.com", "docs.google.com", "dropbox.com", "dl.dropboxusercontent.com",
            "onedrive.live.com", "1drv.ms", "sharepoint.com");

    static boolean isCloudLink(String url) {
        if (url == null || url.length() > 255) {
            return false;
        }
        try {
            java.net.URI uri = new java.net.URI(url);
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getUserInfo() == null && host != null
                    && CLOUD_HOSTS.stream().anyMatch(allowed -> host.equalsIgnoreCase(allowed) || host.toLowerCase().endsWith("." + allowed));
        } catch (java.net.URISyntaxException e) {
            return false;
        }
    }

    // `url` is either a PDF this user uploaded, or a link to one on Drive / Dropbox / OneDrive.
    private void checkCvSource(Resume resume) throws IdInvalidException {
        String url = resume.getUrl().trim();
        resume.setUrl(url);
        if (url.startsWith("https://")) {
            if (!isCloudLink(url)) {
                throw new IdInvalidException("Liên kết CV chỉ hỗ trợ Google Drive, Dropbox hoặc OneDrive (https, tối đa 255 ký tự).");
            }
            return;
        }
        if (!url.matches("[A-Za-z0-9._-]+") || !FileService.isUploadedBy(url, resume.getUser().getId())) {
            throw new IdInvalidException("Tệp CV không hợp lệ.");
        }
        try {
            if (this.fileService.getFileLength(url, "resume") == 0) {
                throw new IdInvalidException("Không tìm thấy tệp CV, vui lòng tải lên lại.");
            }
        } catch (java.net.URISyntaxException e) {
            throw new IdInvalidException("Tệp CV không hợp lệ.");
        }
    }

    public ResCreateResumeDTO create(Resume resume) throws IdInvalidException {
        checkCvSource(resume);
        if (resume.getCoverLetter() != null) {
            String letter = resume.getCoverLetter().trim();
            resume.setCoverLetter(letter.isEmpty() ? null : letter);
        }
        Job job = this.jobRepository.findById(resume.getJob().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Tin tuyển dụng không tồn tại"));
        // a closed or expired posting no longer takes applications
        if (job.isLocked()) {
            throw new IdInvalidException("Tin tuyển dụng này đang bị khóa.");
        }
        if (!job.isActive() || (job.getEndDate() != null && job.getEndDate().isBefore(Instant.now()))) {
            throw new IdInvalidException("Tin tuyển dụng này đã đóng hoặc hết hạn.");
        }
        if (job.getStartDate() != null && job.getStartDate().isAfter(Instant.now())) {
            throw new IdInvalidException("Tin tuyển dụng này chưa mở nhận hồ sơ.");
        }
        if (hasApplied(resume.getUser().getId(), job.getId())) {
            throw new ConflictException("Bạn đã ứng tuyển vào vị trí này rồi.");
        }
        try {
            resume = this.resumeRepository.saveAndFlush(resume);
        } catch (DataIntegrityViolationException e) {
            // two requests raced past the check above; the unique (user_id, job_id) constraint caught the second one
            throw new ConflictException("Bạn đã ứng tuyển vào vị trí này rồi.");
        }
        this.notificationService.applicationSent(resume, job);

        ResCreateResumeDTO res = new ResCreateResumeDTO();
        res.setId(resume.getId());
        res.setCreatedBy(resume.getCreatedBy());
        res.setCreatedAt(resume.getCreatedAt());

        return res;
    }

    public ResUpdateResumeDTO update(Resume resume) {
        resume = this.resumeRepository.save(resume);
        ResUpdateResumeDTO res = new ResUpdateResumeDTO();
        res.setUpdatedAt(resume.getUpdatedAt());
        res.setUpdatedBy(resume.getUpdatedBy());
        return res;
    }

    public void delete(long id) {
        this.resumeRepository.deleteById(id);
    }

    // The candidate's own list: everything except the employer's private evaluation.
    public ResFetchResumeDTO getResumeForCandidate(Resume resume) {
        ResFetchResumeDTO res = getResume(resume);
        res.setScore(null);
        res.setRemark(null);
        return res;
    }

    public ResFetchResumeDTO getResume(Resume resume) {
        ResFetchResumeDTO res = new ResFetchResumeDTO();
        res.setId(resume.getId());
        res.setEmail(resume.getEmail());
        res.setUrl(resume.getUrl());
        res.setStatus(resume.getStatus());
        res.setCoverLetter(resume.getCoverLetter());
        res.setScore(resume.getScore());
        res.setRemark(resume.getRemark());
        res.setInterviewAt(resume.getInterviewAt());
        res.setMeetingLink(resume.getMeetingLink());
        res.setDecisionNote(resume.getDecisionNote());
        res.setCreatedAt(resume.getCreatedAt());
        res.setCreatedBy(resume.getCreatedBy());
        res.setUpdatedAt(resume.getUpdatedAt());
        res.setUpdatedBy(resume.getUpdatedBy());

        if (resume.getJob() != null && resume.getJob().getCompany() != null) {
            res.setCompanyName(resume.getJob().getCompany().getName());
        }

        res.setUser(new ResFetchResumeDTO.UserResume(resume.getUser().getId(), resume.getUser().getName()));
        res.setJob(new ResFetchResumeDTO.JobResume(resume.getJob().getId(), resume.getJob().getName()));

        return res;
    }

    public ResultPaginationDTO fetchAllResume(Specification<Resume> spec, Pageable pageable) {
        Company mine = this.userService.currentUserCompany();
        if (mine != null) {
            long companyId = mine.getId();
            Specification<Resume> ofCompany = (root, query, cb) -> cb.equal(root.get("job").get("company").get("id"), companyId);
            spec = ofCompany.and(spec);
        }
        Page<Resume> pageUser = this.resumeRepository.findAll(spec, pageable);
        ResultPaginationDTO rs = new ResultPaginationDTO();
        ResultPaginationDTO.Meta mt = new ResultPaginationDTO.Meta();

        mt.setPage(pageable.getPageNumber() + 1);
        mt.setPageSize(pageable.getPageSize());

        mt.setPages(pageUser.getTotalPages());
        mt.setTotal(pageUser.getTotalElements());

        rs.setMeta(mt);

        // remove sensitive data
        List<ResFetchResumeDTO> listResume = pageUser.getContent()
                .stream().map(item -> this.getResume(item))
                .collect(Collectors.toList());
        // mark applicants whose plan highlights them (one query for the whole page)
        Map<Long, PlanEnum> plans = this.planService.activePlans(
                listResume.stream().map(r -> r.getUser().getId()).distinct().collect(Collectors.toList()));
        for (ResFetchResumeDTO row : listResume) {
            PlanEnum plan = plans.get(row.getUser().getId());
            if (plan != null && plan.isHighlight()) {
                row.setApplicantPlan(plan.name());
            }
        }

        rs.setResult(listResume);

        return rs;
    }

    public ResultPaginationDTO fetchResumeByUser(Pageable pageable) {
        // by user id, not by an e-mail string pasted into a filter (an address may contain an apostrophe)
        User me = this.userRepository.findByEmail(SecurityUtil.getCurrentUserLogin().orElse(""));
        long userId = me == null ? -1 : me.getId();
        Specification<Resume> mine = (root, query, cb) -> cb.equal(root.get("user").get("id"), userId);
        Page<Resume> pageResume = this.resumeRepository.findAll(mine, pageable);

        ResultPaginationDTO rs = new ResultPaginationDTO();
        ResultPaginationDTO.Meta mt = new ResultPaginationDTO.Meta();

        mt.setPage(pageable.getPageNumber() + 1);
        mt.setPageSize(pageable.getPageSize());

        mt.setPages(pageResume.getTotalPages());
        mt.setTotal(pageResume.getTotalElements());

        rs.setMeta(mt);

        // remove sensitive data
        List<ResFetchResumeDTO> listResume = pageResume.getContent()
                .stream().map(item -> this.getResumeForCandidate(item))
                .collect(Collectors.toList());

        rs.setResult(listResume);

        return rs;
    }
}
