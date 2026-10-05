package vn.hoidanit.jobhunter.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PageableDefault;
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
import vn.hoidanit.jobhunter.domain.Review;
import vn.hoidanit.jobhunter.domain.request.ReqAdminReviewDTO;
import vn.hoidanit.jobhunter.domain.request.ReqReviewDTO;
import vn.hoidanit.jobhunter.domain.response.ResultPaginationDTO;
import vn.hoidanit.jobhunter.domain.response.review.ResCompanyReviewsDTO;
import vn.hoidanit.jobhunter.domain.response.review.ResReviewDTO;
import vn.hoidanit.jobhunter.service.ReviewService;
import vn.hoidanit.jobhunter.util.annotation.ApiMessage;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;
import vn.hoidanit.jobhunter.util.error.PermissionException;

@RestController
@RequestMapping("/api/v1")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/companies/{companyId}/reviews")
    @ApiMessage("Fetch reviews of a company")
    public ResponseEntity<ResCompanyReviewsDTO> fetchByCompany(
            @PathVariable("companyId") long companyId,
            @PageableDefault(size = 5, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable)
            throws IdInvalidException {
        return ResponseEntity.ok(this.reviewService.fetchByCompany(companyId, pageable));
    }

    @GetMapping("/me/reviews/{companyId}")
    @ApiMessage("Fetch my review of a company")
    public ResponseEntity<ResReviewDTO> fetchMine(@PathVariable("companyId") long companyId) throws IdInvalidException {
        return ResponseEntity.ok(this.reviewService.fetchMine(companyId));
    }

    @PutMapping("/me/reviews/{companyId}")
    @ApiMessage("Save my review of a company")
    public ResponseEntity<ResReviewDTO> saveMine(@PathVariable("companyId") long companyId,
            @Valid @RequestBody ReqReviewDTO req) throws IdInvalidException, PermissionException {
        return ResponseEntity.ok(this.reviewService.saveMine(companyId, req));
    }

    @DeleteMapping("/me/reviews/{companyId}")
    @ApiMessage("Delete my review of a company")
    public ResponseEntity<Void> deleteMine(@PathVariable("companyId") long companyId) throws IdInvalidException {
        this.reviewService.deleteMine(companyId);
        return ResponseEntity.ok().body(null);
    }

    @GetMapping("/reviews")
    @ApiMessage("Fetch reviews with pagination")
    public ResponseEntity<ResultPaginationDTO> fetchAll(@Filter Specification<Review> spec, Pageable pageable) {
        return ResponseEntity.ok(this.reviewService.fetchAll(spec, pageable));
    }

    @PostMapping("/reviews")
    @ApiMessage("Create a review")
    public ResponseEntity<ResReviewDTO> create(@Valid @RequestBody ReqAdminReviewDTO req) throws IdInvalidException {
        return ResponseEntity.status(HttpStatus.CREATED).body(this.reviewService.adminCreate(req));
    }

    @PutMapping("/reviews")
    @ApiMessage("Update a review")
    public ResponseEntity<ResReviewDTO> update(@Valid @RequestBody ReqAdminReviewDTO req) throws IdInvalidException {
        return ResponseEntity.ok(this.reviewService.adminUpdate(req));
    }

    @DeleteMapping("/reviews/{id}")
    @ApiMessage("Delete a review")
    public ResponseEntity<Void> delete(@PathVariable("id") long id) throws IdInvalidException {
        this.reviewService.delete(id);
        return ResponseEntity.ok().body(null);
    }
}
