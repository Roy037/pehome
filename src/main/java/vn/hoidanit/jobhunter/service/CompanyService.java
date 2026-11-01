package vn.hoidanit.jobhunter.service;

import org.springframework.transaction.annotation.Transactional;
import vn.hoidanit.jobhunter.repository.JobRepository;
import vn.hoidanit.jobhunter.repository.PlanOrderRepository;
import vn.hoidanit.jobhunter.util.error.ConflictException;
import vn.hoidanit.jobhunter.util.error.ResourceNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.net.URI;
import java.util.regex.Pattern;
import java.util.Set;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import vn.hoidanit.jobhunter.domain.Company;
import vn.hoidanit.jobhunter.domain.response.ResCompanyVerificationDTO;
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
    private final PlanOrderRepository orderRepository;
    private final NotificationService notificationService;

    public CompanyService(
            CompanyRepository companyRepository,
            UserRepository userRepository,
            JobRepository jobRepository,
            NotificationService notificationService, PlanOrderRepository orderRepository) {
        this.orderRepository = orderRepository;
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
            // the ledger keeps its rows: a company or an employer that paid cannot be deleted
            if (this.orderRepository.existsByCompanyId(id) || users.stream().anyMatch(u -> this.orderRepository.existsByUserId(u.getId()))) {
                throw new ConflictException("Không thể xóa công ty đã có giao dịch thanh toán.");
            }
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
        List<String> missing = verification(company).checks().stream().filter(c -> c.required() && !c.ok())
                .map(ResCompanyVerificationDTO.Check::label).toList();
        if (!missing.isEmpty()) {
            throw new IdInvalidException("Chưa thể duyệt, còn thiếu: " + String.join("; ", missing) + ".");
        }
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
        company.setId(0); // a create never replaces an existing row, whatever id the request body carries
        copyProfile(company, company);
        return this.companyRepository.save(company);
    }

    public Company handleUpdateCompany(Company reqCompany) {
        return handleUpdateCompany(reqCompany, false);
    }

    /**
     * `byEmployer`: an approved company that changes the things its approval rests on (name, tax code, licence) is
     * not hidden again, but the admin is told to look at it.
     */
    public Company handleUpdateCompany(Company reqCompany, boolean byEmployer) {
        Optional<Company> companyOptional = this.companyRepository.findById(reqCompany.getId());
        if (companyOptional.isPresent()) {
            Company currentCompany = companyOptional.get();
            String oldName = currentCompany.getName(), oldTax = currentCompany.getTaxCode(),
                    oldLicense = currentCompany.getLicenseFile();
            currentCompany.setLogo(reqCompany.getLogo());
            currentCompany.setName(reqCompany.getName());
            currentCompany.setDescription(reqCompany.getDescription());
            currentCompany.setAddress(reqCompany.getAddress());
            copyProfile(currentCompany, reqCompany);
            // a request that leaves these out (an older client) keeps what is stored
            String tax = blankToNull(reqCompany.getTaxCode());
            if (tax != null && !tax.equals(oldTax)) {
                if (this.companyRepository.existsByTaxCodeAndIdNot(tax, currentCompany.getId())) {
                    throw new ConflictException("Mã số thuế " + tax + " đã được đăng ký trên hệ thống.");
                }
                currentCompany.setTaxCode(tax);
            }
            String phone = blankToNull(reqCompany.getPhone());
            if (phone != null) {
                currentCompany.setPhone(phone);
            }
            String license = blankToNull(reqCompany.getLicenseFile());
            if (license != null) {
                currentCompany.setLicenseFile(license);
            }
            // save
            Company saved = this.companyRepository.save(currentCompany);
            if (byEmployer && saved.isApproved() && (!Objects.equals(oldName, saved.getName())
                    || !Objects.equals(oldTax, saved.getTaxCode()) || !Objects.equals(oldLicense, saved.getLicenseFile()))) {
                this.notificationService.adminCompanyChanged(saved);
            }
            return saved;
        }
        return null;
    }

    public boolean existsByTaxCode(String taxCode) {
        return this.companyRepository.existsByTaxCode(taxCode.trim());
    }

    private static final Pattern TAX_CODE = Pattern.compile("^\\d{10}(-\\d{3})?$");
    private static final Set<String> FREE_MAIL = Set.of("gmail.com", "googlemail.com", "yahoo.com", "yahoo.com.vn",
            "outlook.com", "hotmail.com", "live.com", "icloud.com", "me.com", "protonmail.com", "proton.me");

    private static String domainOf(String email) {
        int at = email == null ? -1 : email.lastIndexOf('@');
        return at < 0 ? "" : email.substring(at + 1).trim().toLowerCase();
    }

    static boolean isFreeMail(String email) {
        return FREE_MAIL.contains(domainOf(email));
    }

    /** The host of a website address without "www.", or null when it is empty or not an address. */
    static String hostOf(String website) {
        if (website == null || website.isBlank()) {
            return null;
        }
        try {
            String host = URI.create(website.trim()).getHost();
            return host == null ? null : host.toLowerCase().replaceFirst("^www\\.", "");
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** True when the e-mail's domain is the website's host or one of its subdomains (hr@mail.acme.vn for acme.vn). */
    static boolean sameDomain(String email, String host) {
        String domain = domainOf(email);
        return host != null && !domain.isEmpty() && (domain.equals(host) || domain.endsWith("." + host));
    }

    public ResCompanyVerificationDTO verification(Company company) {
        List<User> employers = this.userRepository.findByCompany(company);
        String host = hostOf(company.getWebsite());
        List<ResCompanyVerificationDTO.Contact> contacts = employers.stream()
                .map(user -> new ResCompanyVerificationDTO.Contact(user.getName(), user.getEmail(), user.isEmailVerified(),
                        isFreeMail(user.getEmail()), host == null ? null : sameDomain(user.getEmail(), host)))
                .toList();
        String tax = company.getTaxCode();
        boolean taxOk = tax != null && TAX_CODE.matcher(tax).matches()
                && !this.companyRepository.existsByTaxCodeAndIdNot(tax, company.getId());
        boolean verified = employers.isEmpty() || employers.stream().anyMatch(User::isEmailVerified);
        boolean hasLicense = company.getLicenseFile() != null && !company.getLicenseFile().isBlank();
        boolean hasPhone = company.getPhone() != null && !company.getPhone().isBlank();
        boolean domainOk = contacts.stream().anyMatch(c -> Boolean.TRUE.equals(c.domainMatch()) && !c.freeMail());
        String domainNote = host == null ? "Công ty chưa có website để đối chiếu"
                : !employers.isEmpty() && contacts.stream().allMatch(ResCompanyVerificationDTO.Contact::freeMail)
                        ? "Người liên hệ đang dùng email miễn phí"
                        : null;
        List<ResCompanyVerificationDTO.Check> checks = List.of(
                new ResCompanyVerificationDTO.Check("email", "Email người liên hệ đã xác thực", verified, true,
                        employers.isEmpty() ? "Công ty chưa có tài khoản nhà tuyển dụng" : null),
                new ResCompanyVerificationDTO.Check("taxCode", "Mã số thuế hợp lệ và không trùng", taxOk, true,
                        tax == null ? "Chưa nhập mã số thuế" : taxOk ? null : "Sai định dạng hoặc đã có công ty khác dùng"),
                new ResCompanyVerificationDTO.Check("license", "Đã tải giấy phép kinh doanh", hasLicense, true, null),
                new ResCompanyVerificationDTO.Check("phone", "Có số điện thoại liên hệ", hasPhone, false, null),
                new ResCompanyVerificationDTO.Check("domain", "Email công việc cùng tên miền với website", domainOk, false,
                        domainNote));
        int score = (int) checks.stream().filter(ResCompanyVerificationDTO.Check::ok).count();
        return new ResCompanyVerificationDTO(tax, company.getPhone(), company.getWebsite(), hasLicense, contacts, checks,
                score);
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
