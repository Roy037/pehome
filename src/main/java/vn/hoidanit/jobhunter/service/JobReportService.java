package vn.hoidanit.jobhunter.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import vn.hoidanit.jobhunter.domain.Job;
import vn.hoidanit.jobhunter.domain.JobReport;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.domain.request.ReqJobReportDTO;
import vn.hoidanit.jobhunter.domain.response.ResultPaginationDTO;
import vn.hoidanit.jobhunter.domain.response.jobreport.ResJobReportDTO;
import vn.hoidanit.jobhunter.repository.JobReportRepository;
import vn.hoidanit.jobhunter.repository.JobRepository;
import vn.hoidanit.jobhunter.util.error.ConflictException;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;
import vn.hoidanit.jobhunter.util.error.ResourceNotFoundException;

@Service
public class JobReportService {

    private static final String ALREADY_REPORTED = "Bạn đã báo cáo tin tuyển dụng này rồi";

    private final JobReportRepository reportRepository;
    private final JobRepository jobRepository;
    private final UserService userService;
    private final NotificationService notificationService;

    public JobReportService(JobReportRepository reportRepository, JobRepository jobRepository,
            UserService userService, NotificationService notificationService) {
        this.notificationService = notificationService;
        this.reportRepository = reportRepository;
        this.jobRepository = jobRepository;
        this.userService = userService;
    }

    public void report(ReqJobReportDTO req) throws IdInvalidException {
        User user = this.userService.handleGetCurrentUser();
        Job job = this.jobRepository.findById(req.getJobId())
                .orElseThrow(() -> new ResourceNotFoundException("Job với id = " + req.getJobId() + " không tồn tại"));
        if (this.reportRepository.existsByUserIdAndJobId(user.getId(), job.getId())) {
            throw new ConflictException(ALREADY_REPORTED);
        }
        JobReport report = new JobReport();
        report.setUser(user);
        report.setJob(job);
        report.setReason(req.getReason());
        String detail = req.getDetail() == null ? "" : req.getDetail().trim();
        report.setDetail(detail.isEmpty() ? null : detail);
        try {
            this.reportRepository.save(report);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(ALREADY_REPORTED);
        }
        this.notificationService.adminJobReported(report);
    }

    public void dismiss(long id) {
        if (!this.reportRepository.existsById(id)) {
            throw new ResourceNotFoundException("Báo cáo với id = " + id + " không tồn tại");
        }
        this.reportRepository.deleteById(id);
    }

    public ResultPaginationDTO fetchAll(Specification<JobReport> spec, Pageable pageable) {
        Page<JobReport> page = this.reportRepository.findAll(spec, pageable);
        ResultPaginationDTO rs = new ResultPaginationDTO();
        ResultPaginationDTO.Meta meta = new ResultPaginationDTO.Meta();
        meta.setPage(pageable.getPageNumber() + 1);
        meta.setPageSize(pageable.getPageSize());
        meta.setPages(page.getTotalPages());
        meta.setTotal(page.getTotalElements());
        rs.setMeta(meta);
        rs.setResult(page.getContent().stream().map(this::convert).toList());
        return rs;
    }

    private ResJobReportDTO convert(JobReport report) {
        ResJobReportDTO dto = new ResJobReportDTO();
        dto.setId(report.getId());
        dto.setCreatedAt(report.getCreatedAt());
        dto.setReason(report.getReason());
        dto.setDetail(report.getDetail());
        User user = report.getUser();
        dto.setUser(new ResJobReportDTO.UserRef(user.getId(), user.getName(), user.getEmail()));
        Job job = report.getJob();
        dto.setJob(new ResJobReportDTO.JobRef(job.getId(), job.getName(),
                job.getCompany() != null ? job.getCompany().getName() : null, job.isLocked(), job.getLockReason()));
        return dto;
    }
}
