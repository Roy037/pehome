package vn.hoidanit.jobhunter.service;

import vn.hoidanit.jobhunter.repository.ResumeRepository;
import vn.hoidanit.jobhunter.util.error.ConflictException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

import vn.hoidanit.jobhunter.domain.Company;
import vn.hoidanit.jobhunter.domain.Job;
import vn.hoidanit.jobhunter.domain.Skill;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.domain.response.ResultPaginationDTO;
import vn.hoidanit.jobhunter.domain.response.job.ResCreateJobDTO;
import vn.hoidanit.jobhunter.domain.response.job.ResUpdateJobDTO;
import vn.hoidanit.jobhunter.repository.CompanyRepository;
import vn.hoidanit.jobhunter.repository.JobRepository;
import vn.hoidanit.jobhunter.repository.SkillRepository;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;
import vn.hoidanit.jobhunter.util.error.PermissionException;
import vn.hoidanit.jobhunter.util.error.ResourceNotFoundException;

@Service
public class JobService {

    private final JobRepository jobRepository;
    private final SkillRepository skillRepository;
    private final CompanyRepository companyRepository;
    private final UserService userService;
    private final PlanService planService;
    private final ResumeRepository resumeRepository;
    private final NotificationService notificationService;

    public JobService(JobRepository jobRepository,
            SkillRepository skillRepository,
            CompanyRepository companyRepository,
            UserService userService,
            ResumeRepository resumeRepository,
            NotificationService notificationService, PlanService planService) {
        this.planService = planService;
        this.notificationService = notificationService;
        this.resumeRepository = resumeRepository;
        this.jobRepository = jobRepository;
        this.skillRepository = skillRepository;
        this.companyRepository = companyRepository;
        this.userService = userService;
    }

    // Users that belong to a company (employers) may only touch that company's jobs.
    public void assertOwned(Job job) throws PermissionException {
        Company mine = this.userService.currentUserCompany();
        if (mine != null && (job.getCompany() == null || job.getCompany().getId() != mine.getId())) {
            throw new PermissionException("Tin tuyển dụng này không thuộc công ty của bạn.");
        }
    }

    public Optional<Job> fetchJobById(long id) {
        return this.jobRepository.findById(id);
    }

    private static void checkDates(Job j) throws IdInvalidException {
        if (j.getStartDate() != null && j.getEndDate() != null && j.getEndDate().isBefore(j.getStartDate())) {
            throw new IdInvalidException("Hạn nộp hồ sơ phải sau ngày bắt đầu tuyển.");
        }
    }

    // 0 means "not given"; a maximum below the minimum is a typo
    private static void checkSalary(Job j) throws IdInvalidException {
        if (j.getSalaryMax() != null && j.getSalaryMax() == 0) {
            j.setSalaryMax(null);
        }
        if (j.getSalaryMax() != null && j.getSalaryMax() < j.getSalary()) {
            throw new IdInvalidException("Lương tối đa phải lớn hơn hoặc bằng lương tối thiểu.");
        }
    }

    public ResCreateJobDTO create(Job j) throws PermissionException, IdInvalidException {
        j.setId(0); // a create never replaces an existing row, whatever id the request body carries
        j.setPinnedUntil(null); // only a paid order pins a job
        checkDates(j);
        checkSalary(j);
        Company mine = this.userService.currentUserCompany();
        if (mine != null) {
            if (!mine.isApproved()) {
                throw new PermissionException("Công ty của bạn đang chờ quản trị viên duyệt, chưa thể đăng tin.");
            }
            User me = this.userService.currentUserOrNull();
            if (me != null && !User.TERMS_VERSION.equals(me.getTermsVersion())) {
                throw new PermissionException("Bạn cần đồng ý Điều khoản sử dụng dành cho nhà tuyển dụng trước khi đăng tin.");
            }
            if (j.isActive() && !expired(j)) {
                assertRoomToOpen(mine, 0);
            }
            j.setCompany(mine);
        }

        // check skills
        if (j.getSkills() != null) {
            List<Long> reqSkills = j.getSkills()
                    .stream().map(x -> x.getId())
                    .collect(Collectors.toList());

            List<Skill> dbSkills = this.skillRepository.findByIdIn(reqSkills);
            j.setSkills(dbSkills);
        }

        // check company
        if (j.getCompany() != null) {
            Optional<Company> cOptional = this.companyRepository.findById(j.getCompany().getId());
            if (cOptional.isPresent()) {
                j.setCompany(cOptional.get());
            }
        }

        // create job
        Job currentJob = this.jobRepository.save(j);

        // convert response
        ResCreateJobDTO dto = new ResCreateJobDTO();
        dto.setId(currentJob.getId());
        dto.setName(currentJob.getName());
        dto.setSalary(currentJob.getSalary());
        dto.setSalaryMax(currentJob.getSalaryMax());
        dto.setQuantity(currentJob.getQuantity());
        dto.setLocation(currentJob.getLocation());
        dto.setLevel(currentJob.getLevel());
        dto.setEmploymentType(currentJob.getEmploymentType());
        dto.setWorkMode(currentJob.getWorkMode());
        dto.setStartDate(currentJob.getStartDate());
        dto.setEndDate(currentJob.getEndDate());
        dto.setActive(currentJob.isActive());
        dto.setCreatedAt(currentJob.getCreatedAt());
        dto.setCreatedBy(currentJob.getCreatedBy());

        if (currentJob.getSkills() != null) {
            List<String> skills = currentJob.getSkills()
                    .stream().map(item -> item.getName())
                    .collect(Collectors.toList());
            dto.setSkills(skills);
        }

        return dto;
    }

    public ResUpdateJobDTO update(Job j, Job jobInDB) throws PermissionException, IdInvalidException {
        assertOwned(jobInDB);
        checkDates(j);
        checkSalary(j);
        if (this.userService.currentUserCompany() != null) {
            j.setCompany(null); // employers cannot move a job to another company
        }

        // check skills
        if (j.getSkills() != null) {
            List<Long> reqSkills = j.getSkills()
                    .stream().map(x -> x.getId())
                    .collect(Collectors.toList());

            List<Skill> dbSkills = this.skillRepository.findByIdIn(reqSkills);
            jobInDB.setSkills(dbSkills);
        }

        // check company
        if (j.getCompany() != null) {
            Optional<Company> cOptional = this.companyRepository.findById(j.getCompany().getId());
            if (cOptional.isPresent()) {
                jobInDB.setCompany(cOptional.get());
            }
        }

        // a closed job that is switched back on takes one of the company's places for open jobs again
        Company owner = this.userService.currentUserCompany();
        if (owner != null && j.isActive() && !expired(j) && !(jobInDB.isActive() && !jobInDB.isLocked() && !expired(jobInDB))) {
            assertRoomToOpen(owner, jobInDB.getId());
        }

        // update correct info
        jobInDB.setName(j.getName());
        jobInDB.setSalary(j.getSalary());
        jobInDB.setSalaryMax(j.getSalaryMax());
        jobInDB.setQuantity(j.getQuantity());
        jobInDB.setLocation(j.getLocation());
        jobInDB.setLevel(j.getLevel());
        jobInDB.setEmploymentType(j.getEmploymentType());
        jobInDB.setWorkMode(j.getWorkMode());
        jobInDB.setStartDate(j.getStartDate());
        jobInDB.setEndDate(j.getEndDate());
        jobInDB.setActive(j.isActive());

        // update job
        Job currentJob = this.jobRepository.save(jobInDB);

        // convert response
        ResUpdateJobDTO dto = new ResUpdateJobDTO();
        dto.setId(currentJob.getId());
        dto.setName(currentJob.getName());
        dto.setSalary(currentJob.getSalary());
        dto.setSalaryMax(currentJob.getSalaryMax());
        dto.setQuantity(currentJob.getQuantity());
        dto.setLocation(currentJob.getLocation());
        dto.setLevel(currentJob.getLevel());
        dto.setEmploymentType(currentJob.getEmploymentType());
        dto.setWorkMode(currentJob.getWorkMode());
        dto.setStartDate(currentJob.getStartDate());
        dto.setEndDate(currentJob.getEndDate());
        dto.setActive(currentJob.isActive());
        dto.setUpdatedAt(currentJob.getUpdatedAt());
        dto.setUpdatedBy(currentJob.getUpdatedBy());

        if (currentJob.getSkills() != null) {
            List<String> skills = currentJob.getSkills()
                    .stream().map(item -> item.getName())
                    .collect(Collectors.toList());
            dto.setSkills(skills);
        }

        return dto;
    }

    public void delete(long id) {
        // résumés point at the job: deleting it would orphan (or fail on) real applications
        if (this.resumeRepository.existsByJobId(id)) {
            throw new ConflictException(
                    "Không thể xóa tin tuyển dụng đã có hồ sơ ứng tuyển. Hãy tắt trạng thái \"đang tuyển\" để đóng tin thay vì xóa.");
        }
        this.jobRepository.deleteById(id);
    }

    private static boolean expired(Job job) {
        return job.getEndDate() != null && !job.getEndDate().isAfter(Instant.now());
    }

    // ponytail: a company that already has more open jobs than its places (it posted them before the limit existed)
    // keeps them; it just cannot open another until it closes some or buys a pack.
    private void assertRoomToOpen(Company company, long exceptJobId) throws PermissionException {
        Instant now = Instant.now();
        long open = this.jobRepository.countOpen(company.getId(), exceptJobId, now);
        int limit = this.planService.employerJobLimit(company.getId(), now);
        if (open >= limit) {
            throw new PermissionException("Công ty đã có " + open + "/" + limit
                    + " tin đang mở. Hãy đóng bớt tin hoặc mua thêm chỗ đăng tin ở mục Dịch vụ.");
        }
    }

    // A locked post, or a post of a company that is not approved yet, is only visible to admins and to the company
    // that owns it.
    public boolean isVisible(Job job) {
        Company company = job.getCompany();
        if (!job.isLocked() && (company == null || company.isApproved())) {
            return true;
        }
        User me = this.userService.currentUserOrNull();
        return UserService.isSuperAdmin(me)
                || (me != null && me.getCompany() != null && company != null && me.getCompany().getId() == company.getId());
    }

    public Job lock(long id, String reason) {
        Job job = this.jobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Job với id = " + id + " không tồn tại"));
        job.setLocked(true);
        job.setLockReason(reason.trim());
        job.setLockedAt(java.time.Instant.now());
        job = this.jobRepository.save(job);
        this.notificationService.jobLocked(job);
        return job;
    }

    public Job unlock(long id) {
        Job job = this.jobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Job với id = " + id + " không tồn tại"));
        job.setLocked(false);
        job.setLockReason(null);
        job.setLockedAt(null);
        job = this.jobRepository.save(job);
        this.notificationService.jobUnlocked(job);
        return job;
    }

    public ResultPaginationDTO fetchAll(Specification<Job> spec, Pageable pageable) {
        User me = this.userService.currentUserOrNull();
        if (!UserService.isSuperAdmin(me)) {
            Long mine = me != null && me.getCompany() != null ? me.getCompany().getId() : null;
            // the public sees open posts of approved companies; an employer also sees every post of their own company
            Specification<Job> visible = (root, query, cb) -> {
                Join<Job, Company> company = root.join("company", JoinType.LEFT);
                Predicate publicPost = cb.and(cb.isFalse(root.get("locked")),
                        cb.or(cb.isNull(company.get("id")), cb.isTrue(company.get("approved"))));
                return mine == null ? publicPost : cb.or(publicPost, cb.equal(company.get("id"), mine));
            };
            spec = visible.and(spec);
        }
        Page<Job> pageUser = this.jobRepository.findAll(spec, pageable);

        ResultPaginationDTO rs = new ResultPaginationDTO();
        ResultPaginationDTO.Meta mt = new ResultPaginationDTO.Meta();

        mt.setPage(pageable.getPageNumber() + 1);
        mt.setPageSize(pageable.getPageSize());

        mt.setPages(pageUser.getTotalPages());
        mt.setTotal(pageUser.getTotalElements());

        rs.setMeta(mt);

        rs.setResult(pageUser.getContent());

        return rs;
    }
}