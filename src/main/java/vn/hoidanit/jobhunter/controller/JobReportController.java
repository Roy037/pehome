package vn.hoidanit.jobhunter.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.turkraft.springfilter.boot.Filter;

import jakarta.validation.Valid;
import vn.hoidanit.jobhunter.domain.JobReport;
import vn.hoidanit.jobhunter.domain.request.ReqJobReportDTO;
import vn.hoidanit.jobhunter.domain.response.ResultPaginationDTO;
import vn.hoidanit.jobhunter.service.JobReportService;
import vn.hoidanit.jobhunter.util.annotation.ApiMessage;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;

@RestController
@RequestMapping("/api/v1")
public class JobReportController {

    private final JobReportService reportService;

    public JobReportController(JobReportService reportService) {
        this.reportService = reportService;
    }

    // Under /me/** so any signed-in user may report; reading the reports is an admin permission below.
    @PostMapping("/me/job-reports")
    @ApiMessage("Report a job post")
    public ResponseEntity<Void> report(@Valid @RequestBody ReqJobReportDTO req) throws IdInvalidException {
        this.reportService.report(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(null);
    }

    @DeleteMapping("/job-reports/{id}")
    @ApiMessage("Dismiss a job report")
    public ResponseEntity<Void> dismiss(@PathVariable("id") long id) {
        this.reportService.dismiss(id);
        return ResponseEntity.ok().body(null);
    }

    @GetMapping("/job-reports")
    @ApiMessage("Fetch job reports")
    public ResponseEntity<ResultPaginationDTO> fetchAll(@Filter Specification<JobReport> spec, Pageable pageable) {
        return ResponseEntity.ok(this.reportService.fetchAll(spec, pageable));
    }
}
