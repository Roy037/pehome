package vn.hoidanit.jobhunter.service;

import vn.hoidanit.jobhunter.util.error.ResourceNotFoundException;
import vn.hoidanit.jobhunter.util.error.ConflictException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import vn.hoidanit.jobhunter.domain.Company;
import vn.hoidanit.jobhunter.domain.Review;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.domain.request.ReqAdminReviewDTO;
import vn.hoidanit.jobhunter.domain.request.ReqReviewDTO;
import vn.hoidanit.jobhunter.domain.response.ResultPaginationDTO;
import vn.hoidanit.jobhunter.domain.response.review.ResCompanyReviewsDTO;
import vn.hoidanit.jobhunter.domain.response.review.ResReviewDTO;
import vn.hoidanit.jobhunter.repository.CompanyRepository;
import vn.hoidanit.jobhunter.repository.ReviewRepository;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;
import vn.hoidanit.jobhunter.util.error.PermissionException;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final CompanyRepository companyRepository;
    private final UserService userService;
    private final CompanyService companyService;
    private final PlanService planService;
    private final NotificationService notificationService;

    public ReviewService(ReviewRepository reviewRepository, CompanyRepository companyRepository,
            UserService userService, CompanyService companyService, PlanService planService,
            NotificationService notificationService) {
        this.notificationService = notificationService;
        this.planService = planService;
        this.reviewRepository = reviewRepository;
        this.companyRepository = companyRepository;
        this.userService = userService;
        this.companyService = companyService;
    }

    public ResCompanyReviewsDTO fetchByCompany(long companyId, Pageable pageable) throws IdInvalidException {
        if (!this.companyService.isVisible(this.companyRepository.findById(companyId).orElse(null))) {
            throw new ResourceNotFoundException("Công ty với id = " + companyId + " không tồn tại");
        }

        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (int star = 5; star >= 1; star--) {
            distribution.put(star, 0L);
        }
        long total = 0;
        long sum = 0;
        for (Object[] row : this.reviewRepository.countByRating(companyId)) {
            int star = ((Number) row[0]).intValue();
            long count = ((Number) row[1]).longValue();
            distribution.put(star, count);
            total += count;
            sum += star * count;
        }

        Page<Review> page = this.reviewRepository.findByCompanyId(companyId, pageable);
        ResCompanyReviewsDTO dto = new ResCompanyReviewsDTO();
        dto.setAverage(total == 0 ? 0 : Math.round(sum * 10.0 / total) / 10.0);
        dto.setTotal(total);
        dto.setDistribution(distribution);
        dto.setMeta(toMeta(page, pageable));
        // one query for the whole page tells which reviewers hold a plan
        java.util.Set<Long> vipIds = this.planService.activePlans(
                page.getContent().stream().map(r -> r.getUser().getId()).distinct().toList()).keySet();
        dto.setResult(page.getContent().stream().map(r -> {
            ResReviewDTO row = convert(r);
            row.setVip(vipIds.contains(r.getUser().getId()));
            return row;
        }).toList());
        return dto;
    }

    public ResReviewDTO fetchMine(long companyId) throws IdInvalidException {
        User user = this.userService.handleGetCurrentUser();
        return this.reviewRepository.findByUserIdAndCompanyId(user.getId(), companyId)
                .map(r -> {
                    ResReviewDTO row = convert(r);
                    row.setVip(this.planService.activePlan(user.getId()) != null);
                    return row;
                }).orElse(null);
    }

    public ResReviewDTO saveMine(long companyId, ReqReviewDTO req) throws IdInvalidException, PermissionException {
        User user = this.userService.currentCandidate();
        Company company = this.companyRepository.findById(companyId)
                .filter(Company::isApproved)
                .orElseThrow(() -> new ResourceNotFoundException("Công ty với id = " + companyId + " không tồn tại"));

        Review existing = this.reviewRepository.findByUserIdAndCompanyId(user.getId(), companyId).orElse(null);
        Review review = existing == null ? new Review() : existing;
        review.setUser(user);
        review.setCompany(company);
        review.setRating(req.getRating());
        review.setContent(req.getContent().trim());
        review = this.reviewRepository.save(review);
        if (existing == null) {
            this.notificationService.reviewReceived(review);
        }
        return convert(review);
    }

    public void deleteMine(long companyId) throws IdInvalidException {
        User user = this.userService.handleGetCurrentUser();
        this.reviewRepository.findByUserIdAndCompanyId(user.getId(), companyId)
                .ifPresent(this.reviewRepository::delete);
    }

    public ResultPaginationDTO fetchAll(Specification<Review> spec, Pageable pageable) {
        Page<Review> page = this.reviewRepository.findAll(spec, pageable);
        ResultPaginationDTO rs = new ResultPaginationDTO();
        rs.setMeta(toMeta(page, pageable));
        rs.setResult(page.getContent().stream().map(this::convert).toList());
        return rs;
    }

    public ResReviewDTO adminCreate(ReqAdminReviewDTO req) throws IdInvalidException {
        User user = this.userService.fetchUserById(req.getUserId());
        if (user == null) {
            throw new ResourceNotFoundException("Người dùng với id = " + req.getUserId() + " không tồn tại");
        }
        Company company = this.companyRepository.findById(req.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Công ty với id = " + req.getCompanyId() + " không tồn tại"));
        if (this.reviewRepository.findByUserIdAndCompanyId(user.getId(), company.getId()).isPresent()) {
            throw new ConflictException("Người dùng này đã đánh giá công ty này rồi");
        }
        Review review = new Review();
        review.setUser(user);
        review.setCompany(company);
        review.setRating(req.getRating());
        review.setContent(req.getContent().trim());
        return convert(this.reviewRepository.save(review));
    }

    public ResReviewDTO adminUpdate(ReqAdminReviewDTO req) throws IdInvalidException {
        Review review = this.reviewRepository.findById(req.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Đánh giá với id = " + req.getId() + " không tồn tại"));
        review.setRating(req.getRating());
        review.setContent(req.getContent().trim());
        return convert(this.reviewRepository.save(review));
    }

    public void delete(long id) throws IdInvalidException {
        if (!this.reviewRepository.existsById(id)) {
            throw new ResourceNotFoundException("Đánh giá với id = " + id + " không tồn tại");
        }
        this.reviewRepository.deleteById(id);
    }

    private ResultPaginationDTO.Meta toMeta(Page<?> page, Pageable pageable) {
        ResultPaginationDTO.Meta meta = new ResultPaginationDTO.Meta();
        meta.setPage(pageable.getPageNumber() + 1);
        meta.setPageSize(pageable.getPageSize());
        meta.setPages(page.getTotalPages());
        meta.setTotal(page.getTotalElements());
        return meta;
    }

    private ResReviewDTO convert(Review review) {
        ResReviewDTO dto = new ResReviewDTO();
        dto.setId(review.getId());
        dto.setRating(review.getRating());
        dto.setContent(review.getContent());
        dto.setCreatedAt(review.getCreatedAt());
        dto.setUpdatedAt(review.getUpdatedAt());
        dto.setUser(new ResReviewDTO.Ref(review.getUser().getId(), review.getUser().getName()));
        dto.setUserAvatar(review.getUser().getAvatar());
        dto.setCompany(new ResReviewDTO.Ref(review.getCompany().getId(), review.getCompany().getName()));
        return dto;
    }
}
