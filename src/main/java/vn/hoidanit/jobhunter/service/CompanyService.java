package vn.hoidanit.jobhunter.service;

import org.springframework.transaction.annotation.Transactional;
import vn.hoidanit.jobhunter.repository.JobRepository;
import vn.hoidanit.jobhunter.util.error.ConflictException;
import vn.hoidanit.jobhunter.util.error.ResourceNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import vn.hoidanit.jobhunter.domain.Company;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.domain.response.ResultPaginationDTO;
import vn.hoidanit.jobhunter.repository.CompanyRepository;
import vn.hoidanit.jobhunter.repository.UserRepository;
import vn.hoidanit.jobhunter.util.SecurityUtil;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;

@Service
public class CompanyService {
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final JobRepository jobRepository;
    private final NotificationService notificationService;

    public CompanyService(
            CompanyRepository companyRepository,
            UserRepository userRepository,
            JobRepository jobRepository,
            NotificationService notificationService) {
        this.jobRepository = jobRepository;
        this.notificationService = notificationService;
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
    }

    private User currentUser() {
        return this.userRepository.findByEmail(SecurityUtil.getCurrentUserLogin().orElse(""));
    }

    private static boolean isSuperAdmin(User user) {
        return user != null && user.getRole() != null && "SUPER_ADMIN".equals(user.getRole().getName());
    }

    // A company still waiting for approval is not public: only SUPER_ADMIN and its own employers can see it.
    public boolean isVisible(Company company) {
        if (company == null) {
            return false;
        }
        if (company.isApproved()) {
            return true;
        }
        User me = currentUser();
        return isSuperAdmin(me) || (me != null && me.getCompany() != null && me.getCompany().getId() == company.getId());
    }

    // All-or-nothing: the employer accounts are only removed if the company itself can go too.
    @Transactional
    public void handleDeleteCompany(long id) {
        if (this.jobRepository.existsByCompanyId(id)) {
            throw new ConflictException(
                    "Không thể xóa công ty đang có tin tuyển dụng. Hãy xóa hoặc chuyển các tin tuyển dụng của công ty trước.");
        }
        Optional<Company> comOptional = this.companyRepository.findById(id);
        if (comOptional.isPresent()) {
            Company com = comOptional.get();
            // fetch all user belong to this company
            List<User> users = this.userRepository.findByCompany(com);
            this.userRepository.deleteAll(users);
        }

        this.companyRepository.deleteById(id);
    }

    public boolean existsByName(String name) {
        return this.companyRepository.existsByNameIgnoreCase(name.trim());
    }

    public Company approve(long id) throws IdInvalidException {
        Company company = this.companyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Công ty với id = " + id + " không tồn tại"));
        boolean wasPending = !company.isApproved();
        company.setApproved(true);
        company.setRejectionReason(null);
        company.setRejectedAt(null);
        company = this.companyRepository.save(company);
        if (wasPending) {
            this.notificationService.companyApproved(company);
        }
        return company;
    }

    public Company reject(long id, String reason) {
        Company company = this.companyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Công ty với id = " + id + " không tồn tại"));
        company.setApproved(false);
        company.setRejectionReason(reason.trim());
        company.setRejectedAt(Instant.now());
        company = this.companyRepository.save(company);
        this.notificationService.companyRejected(company);
        return company;
    }

    // An employer fixing a rejected profile puts the company back in the review queue.
    public Company resubmitIfRejected(Company company) {
        if (company == null || company.isApproved() || company.getRejectionReason() == null) {
            return company;
        }
        company.setRejectionReason(null);
        company.setRejectedAt(null);
        company = this.companyRepository.save(company);
        this.notificationService.adminCompanyPending(company, true);
        return company;
    }

    public Optional<Company> findById(long id) {
        return this.companyRepository.findById(id);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    // Copies the public profile fields; also called as copyProfile(x, x) to normalise blanks on create.
    private static void copyProfile(Company to, Company from) {
        to.setBanner(blankToNull(from.getBanner()));
        to.setWebsite(blankToNull(from.getWebsite()));
        to.setMapEmbedUrl(blankToNull(from.getMapEmbedUrl()));
        to.setCompanyType(from.getCompanyType());
        to.setFacebookUrl(blankToNull(from.getFacebookUrl()));
        to.setLinkedinUrl(blankToNull(from.getLinkedinUrl()));
        to.setTwitterUrl(blankToNull(from.getTwitterUrl()));
        to.setPinterestUrl(blankToNull(from.getPinterestUrl()));
        to.setInstagramUrl(blankToNull(from.getInstagramUrl()));
        to.setYoutubeUrl(blankToNull(from.getYoutubeUrl()));
    }

    public Company handleCreateCompany(Company company) {
        copyProfile(company, company);
        return this.companyRepository.save(company);
    }

    public Company handleUpdateCompany(Company reqCompany) {
        Optional<Company> companyOptional = this.companyRepository.findById(reqCompany.getId());
        if (companyOptional.isPresent()) {
            Company currentCompany = companyOptional.get();
            currentCompany.setLogo(reqCompany.getLogo());
            currentCompany.setName(reqCompany.getName());
            currentCompany.setDescription(reqCompany.getDescription());
            currentCompany.setAddress(reqCompany.getAddress());
            copyProfile(currentCompany, reqCompany);
            // save
            return this.companyRepository.save(currentCompany);
        }
        return null;
    }

    public ResultPaginationDTO handleGetCompany(Specification<Company> spec, Pageable pageable) {
        User me = currentUser();
        if (!isSuperAdmin(me)) {
            Long mine = me != null && me.getCompany() != null ? me.getCompany().getId() : null;
            Specification<Company> visible = (root, query, cb) -> mine == null
                    ? cb.isTrue(root.get("approved"))
                    : cb.or(cb.isTrue(root.get("approved")), cb.equal(root.get("id"), mine));
            spec = visible.and(spec);
        }
        Page<Company> pageCompany = this.companyRepository.findAll(spec, pageable);
        ResultPaginationDTO rs = new ResultPaginationDTO();
        ResultPaginationDTO.Meta mt = new ResultPaginationDTO.Meta();
        mt.setPage(pageable.getPageNumber() + 1);
        mt.setPageSize(pageable.getPageSize());
        mt.setPages(pageCompany.getTotalPages());
        mt.setTotal(pageCompany.getTotalElements());
        rs.setMeta(mt);
        rs.setResult(pageCompany.getContent());
        return rs;
    }

}
