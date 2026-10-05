package vn.hoidanit.jobhunter.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.RequestParam;
import vn.hoidanit.jobhunter.util.error.ResourceNotFoundException;
import java.util.Optional;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.turkraft.springfilter.boot.Filter;
import jakarta.validation.Valid;
import vn.hoidanit.jobhunter.domain.Resume;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.domain.request.ReqResumeEvaluationDTO;
import vn.hoidanit.jobhunter.domain.request.ReqResumeStatusDTO;
import vn.hoidanit.jobhunter.domain.response.ResultPaginationDTO;
import vn.hoidanit.jobhunter.domain.response.resume.ResCreateResumeDTO;
import vn.hoidanit.jobhunter.domain.response.resume.ResFetchResumeDTO;
import vn.hoidanit.jobhunter.domain.response.resume.ResUpdateResumeDTO;
import vn.hoidanit.jobhunter.service.FileService;
import vn.hoidanit.jobhunter.service.EmailVerificationService;
import vn.hoidanit.jobhunter.service.ResumeService;
import vn.hoidanit.jobhunter.service.UserService;
import vn.hoidanit.jobhunter.util.constant.ResumeStateEnum;
import vn.hoidanit.jobhunter.util.error.PermissionException;
import vn.hoidanit.jobhunter.util.error.StorageException;
import vn.hoidanit.jobhunter.util.annotation.ApiMessage;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/v1")
public class ResumeController {

    private final ResumeService resumeService;
    private final UserService userService;
    private final FileService fileService;

    public ResumeController(ResumeService resumeService, UserService userService, FileService fileService) {
        this.resumeService = resumeService;
        this.userService = userService;
        this.fileService = fileService;
    }

    // Lets the job page show "Đã ứng tuyển" instead of an Apply button.
    @GetMapping("/resumes/check-applied")
    @ApiMessage("Check whether I already applied to a job")
    public ResponseEntity<Map<String, Boolean>> checkApplied(@RequestParam("jobId") long jobId)
            throws IdInvalidException {
        User me = this.userService.handleGetCurrentUser();
        return ResponseEntity.ok(Map.of("applied", this.resumeService.hasApplied(me.getId(), jobId)));
    }

    @GetMapping("/resumes/{id}/document")
    @ApiMessage("Stream a resume document")
    public ResponseEntity<Resource> document(@PathVariable("id") long id)
            throws IdInvalidException, PermissionException, StorageException {
        Resume resume = this.resumeService.fetchById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resume với id = " + id + " không tồn tại"));
        User me = this.userService.handleGetCurrentUser();
        if (!this.resumeService.canViewDocument(resume, me)) {
            throw new PermissionException("Bạn không có quyền xem CV này.");
        }
        if (resume.getUrl().startsWith("https://")) {
            throw new IdInvalidException("CV này là liên kết ngoài (Drive/Dropbox/OneDrive), hãy mở trực tiếp liên kết.");
        }
        return this.fileService.serveResume(resume.getUrl());
    }

    @PostMapping("/resumes")
    @ApiMessage("Create a resume")
    public ResponseEntity<ResCreateResumeDTO> create(@Valid @RequestBody Resume resume)
            throws IdInvalidException, PermissionException {
        // the applicant is always the signed-in candidate, and a new application always starts as PENDING
        User me = this.userService.currentCandidate();
        EmailVerificationService.requireVerified(me);
        resume.setUser(me);
        resume.setEmail(me.getEmail());
        resume.setStatus(ResumeStateEnum.PENDING);
        // the employer's fields are never set by the applicant
        resume.setScore(null);
        resume.setRemark(null);
        resume.setInterviewAt(null);
        resume.setMeetingLink(null);
        resume.setDecisionNote(null);

        boolean isIdExist = this.resumeService.checkResumeExistByUserAndJob(resume);
        if (!isIdExist) {
            throw new ResourceNotFoundException("User id/Job id không tồn tại");
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(this.resumeService.create(resume));
    }

    @PutMapping("/resumes")
    @ApiMessage("Update a resume")
    public ResponseEntity<ResUpdateResumeDTO> update(@RequestBody Resume resume)
            throws IdInvalidException, PermissionException {
        Optional<Resume> reqResumeOptional = this.resumeService.fetchById(resume.getId());
        if (reqResumeOptional.isEmpty()) {
            throw new ResourceNotFoundException("Resume với id = " + resume.getId() + " không tồn tại");
        }

        // same pipeline rules as PUT /resumes/{id}/status (no e-mail from this legacy endpoint)
        ReqResumeStatusDTO change = new ReqResumeStatusDTO();
        change.setStatus(resume.getStatus());
        change.setNotify(false);
        return ResponseEntity.ok().body(this.resumeService.update(this.resumeService.changeStatus(resume.getId(), change)));
    }

    @PutMapping("/resumes/{id}/status")
    @ApiMessage("Move an application to another status (and e-mail the candidate)")
    public ResponseEntity<ResFetchResumeDTO> changeStatus(@PathVariable("id") long id,
            @Valid @RequestBody ReqResumeStatusDTO req) throws IdInvalidException, PermissionException {
        return ResponseEntity.ok(this.resumeService.getResume(this.resumeService.changeStatus(id, req)));
    }

    @PutMapping("/resumes/{id}/evaluation")
    @ApiMessage("Score and comment on an application")
    public ResponseEntity<ResFetchResumeDTO> evaluate(@PathVariable("id") long id,
            @Valid @RequestBody ReqResumeEvaluationDTO req) throws PermissionException {
        return ResponseEntity.ok(this.resumeService.getResume(this.resumeService.evaluate(id, req)));
    }

    @DeleteMapping("/resumes/{id}")
    @ApiMessage("Delete a resume by id")
    public ResponseEntity<Void> delete(@PathVariable("id") long id) throws IdInvalidException, PermissionException {
        Optional<Resume> reqResumeOptional = this.resumeService.fetchById(id);
        if (reqResumeOptional.isEmpty()) {
            throw new ResourceNotFoundException("Resume với id = " + id + " không tồn tại");
        }
        this.resumeService.assertOwned(reqResumeOptional.get());

        this.resumeService.delete(id);
        return ResponseEntity.ok().body(null);
    }

    @GetMapping("/resumes/{id}")
    @ApiMessage("Fetch a resume by id")
    public ResponseEntity<ResFetchResumeDTO> fetchById(@PathVariable("id") long id)
            throws IdInvalidException, PermissionException {
        Optional<Resume> reqResumeOptional = this.resumeService.fetchById(id);
        if (reqResumeOptional.isEmpty()) {
            throw new ResourceNotFoundException("Resume với id = " + id + " không tồn tại");
        }
        this.resumeService.assertOwned(reqResumeOptional.get());

        return ResponseEntity.ok().body(this.resumeService.getResume(reqResumeOptional.get()));
    }

    @GetMapping("/resumes")
    @ApiMessage("Fetch all resume with paginate")
    public ResponseEntity<ResultPaginationDTO> fetchAll(
            @Filter Specification<Resume> spec,
            Pageable pageable) {
        return ResponseEntity.ok().body(this.resumeService.fetchAllResume(spec, pageable));
    }

    @PostMapping("/resumes/by-user")
    @ApiMessage("Get list resumes by user")
    public ResponseEntity<ResultPaginationDTO> fetchResumeByUser(Pageable pageable) {

        return ResponseEntity.ok().body(this.resumeService.fetchResumeByUser(pageable));
    }

}
