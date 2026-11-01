package vn.hoidanit.jobhunter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import vn.hoidanit.jobhunter.domain.Company;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.domain.response.ResCompanyVerificationDTO;
import vn.hoidanit.jobhunter.repository.CompanyRepository;
import vn.hoidanit.jobhunter.repository.JobRepository;
import vn.hoidanit.jobhunter.repository.PlanOrderRepository;
import vn.hoidanit.jobhunter.repository.UserRepository;
import vn.hoidanit.jobhunter.util.error.ConflictException;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;

class CompanyVerificationTests {
    private final CompanyRepository companies = mock(CompanyRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final NotificationService notices = mock(NotificationService.class);
    private final CompanyService service = new CompanyService(this.companies, this.users, mock(JobRepository.class),
            this.notices, mock(PlanOrderRepository.class));

    private static Company company(long id, boolean approved) {
        Company company = new Company();
        company.setId(id);
        company.setName("Acme");
        company.setApproved(approved);
        return company;
    }

    private static User employer(String email, boolean verified) {
        User user = new User();
        user.setName("Hà");
        user.setEmail(email);
        user.setEmailVerified(verified);
        return user;
    }

    @Test
    void comparesTheEmailDomainWithTheWebsite() {
        assertEquals("acme.vn", CompanyService.hostOf("https://www.acme.vn/about"));
        assertNull(CompanyService.hostOf(""));
        assertNull(CompanyService.hostOf("not a url"));
        assertTrue(CompanyService.sameDomain("hr@acme.vn", "acme.vn"));
        assertTrue(CompanyService.sameDomain("hr@mail.acme.vn", "acme.vn"));
        assertFalse(CompanyService.sameDomain("hr@notacme.vn", "acme.vn"), "a lookalike domain");
        assertFalse(CompanyService.sameDomain("hr@acme.vn", null));
        assertTrue(CompanyService.isFreeMail("someone@Gmail.com"));
        assertFalse(CompanyService.isFreeMail("hr@acme.vn"));
    }

    @Test
    void aCompanyWithEverythingPassesTheRequiredChecks() {
        Company company = company(1, false);
        company.setTaxCode("0312345678");
        company.setPhone("0901234567");
        company.setLicenseFile("1-u5-license.pdf");
        company.setWebsite("https://acme.vn");
        when(this.users.findByCompany(company)).thenReturn(List.of(employer("hr@acme.vn", true)));

        ResCompanyVerificationDTO result = this.service.verification(company);

        assertTrue(result.checks().stream().allMatch(ResCompanyVerificationDTO.Check::ok));
        assertEquals(5, result.score());
    }

    @Test
    void approvalIsRefusedWhileRequiredThingsAreMissing() {
        Company company = company(2, false);
        when(this.companies.findById(2L)).thenReturn(Optional.of(company));
        when(this.users.findByCompany(company)).thenReturn(List.of(employer("hr@gmail.com", false)));

        IdInvalidException refused = assertThrows(IdInvalidException.class, () -> this.service.approve(2L));

        assertTrue(refused.getMessage().contains("Email người liên hệ đã xác thực"));
        assertTrue(refused.getMessage().contains("Mã số thuế"));
        assertTrue(refused.getMessage().contains("giấy phép"));
        verify(this.companies, never()).save(any(Company.class));
    }

    @Test
    void approvalGoesThroughWhenTheRequiredChecksPass() throws Exception {
        Company company = company(3, false);
        company.setTaxCode("0312345678");
        company.setLicenseFile("1-u5-license.pdf");
        when(this.companies.findById(3L)).thenReturn(Optional.of(company));
        when(this.companies.save(company)).thenReturn(company);
        when(this.users.findByCompany(company)).thenReturn(List.of(employer("hr@gmail.com", true)));

        assertTrue(this.service.approve(3L).isApproved());
        verify(this.notices).companyApproved(company);
    }

    @Test
    void aTaxCodeUsedByAnotherCompanyIsRefused() {
        Company stored = company(4, true);
        stored.setTaxCode("0312345678");
        when(this.companies.findById(4L)).thenReturn(Optional.of(stored));
        when(this.companies.existsByTaxCodeAndIdNot("0987654321", 4L)).thenReturn(true);
        Company request = company(4, true);
        request.setTaxCode("0987654321");

        assertThrows(ConflictException.class, () -> this.service.handleUpdateCompany(request, true));
    }

    @Test
    void anEditThatLeavesOutTheNewFieldsKeepsWhatIsStoredAndOnlyBigChangesAlertTheAdmin() {
        Company stored = company(5, true);
        stored.setTaxCode("0312345678");
        stored.setPhone("0901234567");
        stored.setLicenseFile("1-u5-license.pdf");
        when(this.companies.findById(5L)).thenReturn(Optional.of(stored));
        when(this.companies.save(stored)).thenReturn(stored);

        Company same = company(5, true); // an older client: no tax code, phone or licence in the body
        this.service.handleUpdateCompany(same, true);
        assertEquals("0312345678", stored.getTaxCode());
        assertEquals("0901234567", stored.getPhone());
        assertEquals("1-u5-license.pdf", stored.getLicenseFile());
        verify(this.notices, never()).adminCompanyChanged(any(Company.class));

        Company renamed = company(5, true);
        renamed.setName("Acme Holdings");
        this.service.handleUpdateCompany(renamed, true);
        verify(this.notices).adminCompanyChanged(stored);
        assertTrue(stored.isApproved(), "still online");
    }

    @Test
    void uploaderIsReadFromTheStoredName() {
        assertEquals(7L, FileService.uploaderOf("1791000000000-u7-license.pdf"));
        assertNull(FileService.uploaderOf("1791000000000-license.pdf"));
        assertNull(FileService.uploaderOf(null));
    }
}
