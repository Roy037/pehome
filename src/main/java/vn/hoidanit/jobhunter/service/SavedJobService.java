package vn.hoidanit.jobhunter.service;

import vn.hoidanit.jobhunter.util.error.ResourceNotFoundException;
import vn.hoidanit.jobhunter.util.error.ConflictException;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import org.springframework.stereotype.Service;

import vn.hoidanit.jobhunter.domain.Job;
import vn.hoidanit.jobhunter.domain.SavedJob;
import vn.hoidanit.jobhunter.domain.request.ReqAdminSavedJobDTO;
import vn.hoidanit.jobhunter.domain.response.ResultPaginationDTO;
import vn.hoidanit.jobhunter.domain.response.savedjob.ResSavedJobDTO;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.repository.JobRepository;
import vn.hoidanit.jobhunter.repository.SavedJobRepository;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;
import vn.hoidanit.jobhunter.util.error.PermissionException;

@Service
public class SavedJobService {

    private final SavedJobRepository savedJobRepository;
    private final JobRepository jobRepository;
    private final UserService userService;
    private final PlanService planService;

    public SavedJobService(SavedJobRepository savedJobRepository, JobRepository jobRepository,
            UserService userService, PlanService planService) {
        this.planService = planService;
        this.savedJobRepository = savedJobRepository;
        this.jobRepository = jobRepository;
        this.userService = userService;
    }

    public List<Job> fetchMine() throws IdInvalidException {
        User user = this.userService.handleGetCurrentUser();
        return this.savedJobRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream().map(SavedJob::getJob).filter(job -> !job.isLocked()).toList();
    }

    public void save(long jobId) throws IdInvalidException, PermissionException {
        User user = this.userService.currentCandidate();
        Job job = this.jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job với id = " + jobId + " không tồn tại"));
        if (this.savedJobRepository.existsByUserIdAndJobId(user.getId(), jobId)) {
            return;
        }
        int cap = this.planService.savedJobCap(user.getId());
        if (this.savedJobRepository.countByUserId(user.getId()) >= cap) {
            throw new PermissionException("Gói " + PlanService.label(this.planService.activePlan(user.getId()))
                    + " chỉ lưu tối đa " + cap + " việc làm. Nâng cấp gói để lưu thêm.");
        }
        SavedJob savedJob = new SavedJob();
        savedJob.setUser(user);
        savedJob.setJob(job);
        this.savedJobRepository.save(savedJob);
    }

    public ResultPaginationDTO adminFetchAll(Specification<SavedJob> spec, Pageable pageable) {
        Page<SavedJob> page = this.savedJobRepository.findAll(spec, pageable);
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

    public ResSavedJobDTO adminCreate(ReqAdminSavedJobDTO req) throws IdInvalidException {
        User user = this.userService.fetchUserById(req.getUserId());
        if (user == null) {
            throw new ResourceNotFoundException("Người dùng với id = " + req.getUserId() + " không tồn tại");
        }
        Job job = this.jobRepository.findById(req.getJobId())
                .orElseThrow(() -> new ResourceNotFoundException("Job với id = " + req.getJobId() + " không tồn tại"));
        if (this.savedJobRepository.existsByUserIdAndJobId(user.getId(), job.getId())) {
            throw new ConflictException("Người dùng này đã lưu việc làm này rồi");
        }
        SavedJob savedJob = new SavedJob();
        savedJob.setUser(user);
        savedJob.setJob(job);
        return convert(this.savedJobRepository.save(savedJob));
    }

    public void adminDelete(long id) throws IdInvalidException {
        if (!this.savedJobRepository.existsById(id)) {
            throw new ResourceNotFoundException("Việc làm đã lưu với id = " + id + " không tồn tại");
        }
        this.savedJobRepository.deleteById(id);
    }

    private ResSavedJobDTO convert(SavedJob savedJob) {
        ResSavedJobDTO dto = new ResSavedJobDTO();
        dto.setId(savedJob.getId());
        dto.setCreatedAt(savedJob.getCreatedAt());
        User user = savedJob.getUser();
        dto.setUser(new ResSavedJobDTO.UserRef(user.getId(), user.getName(), user.getEmail()));
        Job job = savedJob.getJob();
        dto.setJob(new ResSavedJobDTO.JobRef(job.getId(), job.getName(),
                job.getCompany() != null ? job.getCompany().getName() : null));
        return dto;
    }

    public void remove(long jobId) throws IdInvalidException {
        User user = this.userService.handleGetCurrentUser();
        this.savedJobRepository.deleteByUserIdAndJobId(user.getId(), jobId);
    }
}
