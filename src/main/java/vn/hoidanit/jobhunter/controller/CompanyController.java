package vn.hoidanit.jobhunter.controller;


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
import vn.hoidanit.jobhunter.domain.Company;
import vn.hoidanit.jobhunter.domain.request.ReqRejectCompanyDTO;
import vn.hoidanit.jobhunter.domain.response.ResultPaginationDTO;
import vn.hoidanit.jobhunter.service.CompanyService;
import vn.hoidanit.jobhunter.util.error.PermissionException;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;
import vn.hoidanit.jobhunter.util.error.ResourceNotFoundException;
import vn.hoidanit.jobhunter.service.UserService;
import vn.hoidanit.jobhunter.util.annotation.ApiMessage;

@RestController
@RequestMapping("/api/v1")
public class CompanyController {
    private final CompanyService companyService;
    private final UserService userService;

    public CompanyController(CompanyService companyService, UserService userService) {
        this.companyService = companyService;
        this.userService = userService;
    }

    @PostMapping("/companies")
    public ResponseEntity<?> createCompany(@Valid @RequestBody Company reqCompany) {
        return ResponseEntity.status(HttpStatus.CREATED).body(this.companyService.handleCreateCompany(reqCompany));
    }

    @GetMapping("/companies")
    public ResponseEntity<ResultPaginationDTO> getCompany(
            @Filter Specification<Company> spec, Pageable pageable) {

        return ResponseEntity.ok(this.companyService.handleGetCompany(spec, pageable));
    }

    @PutMapping("/companies")
    public ResponseEntity<Company> updateCompany(@Valid @RequestBody Company reqCompany) throws PermissionException {
        Company mine = this.userService.currentUserCompany();
        if (mine != null && mine.getId() != reqCompany.getId()) {
            throw new PermissionException("Bạn chỉ được sửa thông tin công ty của mình.");
        }
        Company updatedCompany = this.companyService.handleUpdateCompany(reqCompany);
        if (mine != null) {
            updatedCompany = this.companyService.resubmitIfRejected(updatedCompany);
        }
        return ResponseEntity.ok(updatedCompany);

    }

    @PutMapping("/companies/{id}/approve")
    @ApiMessage("Approve a company")
    public ResponseEntity<Company> approveCompany(@PathVariable("id") long id) throws IdInvalidException {
        return ResponseEntity.ok(this.companyService.approve(id));
    }

    @PutMapping("/companies/{id}/reject")
    @ApiMessage("Reject a company")
    public ResponseEntity<Company> rejectCompany(@PathVariable("id") long id, @Valid @RequestBody ReqRejectCompanyDTO req) {
        return ResponseEntity.ok(this.companyService.reject(id, req.getReason()));
    }

    @DeleteMapping("/companies/{id}")
    public ResponseEntity<Void> deleteCompany(@PathVariable("id") long id) {
        this.companyService.handleDeleteCompany(id);
        return ResponseEntity.ok(null);
    }

    @GetMapping("/companies/{id}")
    @ApiMessage("fetch company by id")
    public ResponseEntity<Company> fetchCompanyById(@PathVariable("id") long id) {
        Company company = this.companyService.findById(id)
                .filter(this.companyService::isVisible)
                .orElseThrow(() -> new ResourceNotFoundException("Công ty với id = " + id + " không tồn tại"));
        return ResponseEntity.ok().body(company);
    }
}
