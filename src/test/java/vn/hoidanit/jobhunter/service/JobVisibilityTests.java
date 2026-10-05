package vn.hoidanit.jobhunter.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import vn.hoidanit.jobhunter.domain.Company;
import vn.hoidanit.jobhunter.domain.Job;
import vn.hoidanit.jobhunter.domain.Role;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.repository.CompanyRepository;
import vn.hoidanit.jobhunter.repository.JobRepository;
import vn.hoidanit.jobhunter.repository.ResumeRepository;
import vn.hoidanit.jobhunter.repository.SkillRepository;

/** A post of a company that is still waiting for approval (or was rejected) must not be public. */
class JobVisibilityTests {
    private final UserService users = mock(UserService.class);
    private final JobService service = new JobService(mock(JobRepository.class), mock(SkillRepository.class),
            mock(CompanyRepository.class), this.users, mock(ResumeRepository.class), mock(NotificationService.class));

    private static Company company(long id, boolean approved) {
        Company company = new Company();
        company.setId(id);
        company.setApproved(approved);
        return company;
    }

    private static Job job(Company company, boolean locked) {
        Job job = new Job();
        job.setCompany(company);
        job.setLocked(locked);
        return job;
    }

    private static User employerOf(Company company) {
        User user = new User();
        user.setCompany(company);
        return user;
    }

    @Test
    void anApprovedCompanysOpenPostIsPublic() {
        assertTrue(this.service.isVisible(job(company(1, true), false)));
        assertTrue(this.service.isVisible(job(null, false)), "a post with no company stays as it was");
    }

    @Test
    void aPendingCompanysPostIsHiddenFromEveryoneExceptItsOwnEmployersAndAdmins() {
        Company pending = company(2, false);
        Job post = job(pending, false);

        when(this.users.currentUserOrNull()).thenReturn(null);
        assertFalse(this.service.isVisible(post), "anonymous visitor");

        when(this.users.currentUserOrNull()).thenReturn(employerOf(company(3, true)));
        assertFalse(this.service.isVisible(post), "an employer of another company");

        when(this.users.currentUserOrNull()).thenReturn(employerOf(pending));
        assertTrue(this.service.isVisible(post), "its own employer");

        User admin = new User();
        Role role = new Role();
        role.setName("SUPER_ADMIN");
        admin.setRole(role);
        when(this.users.currentUserOrNull()).thenReturn(admin);
        assertTrue(this.service.isVisible(post), "an admin");
    }

    @Test
    void aLockedPostOfAnApprovedCompanyIsStillHidden() {
        when(this.users.currentUserOrNull()).thenReturn(null);
        assertFalse(this.service.isVisible(job(company(1, true), true)));
    }
}
