package vn.hoidanit.jobhunter.controller;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.turkraft.springfilter.boot.Filter;

import jakarta.validation.Valid;
import vn.hoidanit.jobhunter.domain.Job;
import vn.hoidanit.jobhunter.domain.SavedJob;
import vn.hoidanit.jobhunter.domain.request.ReqAdminSavedJobDTO;
import vn.hoidanit.jobhunter.domain.response.ResultPaginationDTO;
import vn.hoidanit.jobhunter.domain.response.savedjob.ResSavedJobDTO;
import vn.hoidanit.jobhunter.service.SavedJobService;
import vn.hoidanit.jobhunter.util.annotation.ApiMessage;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;
import vn.hoidanit.jobhunter.util.error.PermissionException;

@RestController
@RequestMapping("/api/v1")
public class SavedJobController {

    private final SavedJobService savedJobService;

    public SavedJobController(SavedJobService savedJobService) {
        this.savedJobService = savedJobService;
    }

    @GetMapping("/me/saved-jobs")
    @ApiMessage("Fetch my saved jobs")
    public ResponseEntity<List<Job>> fetchMine() throws IdInvalidException {
        return ResponseEntity.ok(this.savedJobService.fetchMine());
    }

    @PutMapping("/me/saved-jobs/{jobId}")
    @ApiMessage("Save a job")
    public ResponseEntity<Void> save(@PathVariable("jobId") long jobId) throws IdInvalidException, PermissionException {
        this.savedJobService.save(jobId);
        return ResponseEntity.ok().body(null);
    }

    @DeleteMapping("/me/saved-jobs/{jobId}")
    @ApiMessage("Remove a saved job")
    public ResponseEntity<Void> remove(@PathVariable("jobId") long jobId) throws IdInvalidException {
        this.savedJobService.remove(jobId);
        return ResponseEntity.ok().body(null);
    }

    @GetMapping("/saved-jobs")
    @ApiMessage("Fetch saved jobs with pagination")
    public ResponseEntity<ResultPaginationDTO> fetchAll(@Filter Specification<SavedJob> spec, Pageable pageable) {
        return ResponseEntity.ok(this.savedJobService.adminFetchAll(spec, pageable));
    }

    @PostMapping("/saved-jobs")
    @ApiMessage("Create a saved job")
    public ResponseEntity<ResSavedJobDTO> create(@Valid @RequestBody ReqAdminSavedJobDTO req)
            throws IdInvalidException {
        return ResponseEntity.status(HttpStatus.CREATED).body(this.savedJobService.adminCreate(req));
    }

    @DeleteMapping("/saved-jobs/{id}")
    @ApiMessage("Delete a saved job")
    public ResponseEntity<Void> delete(@PathVariable("id") long id) throws IdInvalidException {
        this.savedJobService.adminDelete(id);
        return ResponseEntity.ok().body(null);
    }
}
