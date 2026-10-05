package vn.hoidanit.jobhunter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import vn.hoidanit.jobhunter.domain.Company;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.repository.CompanyRepository;
import vn.hoidanit.jobhunter.repository.JobRepository;
import vn.hoidanit.jobhunter.repository.PlanOrderRepository;
import vn.hoidanit.jobhunter.repository.ResumeRepository;
import vn.hoidanit.jobhunter.repository.UserRepository;

/**
 * Sign-up and the admin create endpoints bind the entity straight from the request body. With a numeric id in the
 * body, save() used to update that row (for example the seeded admin) instead of inserting a new one.
 */
class CreateIgnoresIdTests {

    @Test
    void creatingAUserNeverReplacesAnExistingRow() {
        UserRepository users = mock(UserRepository.class);
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        UserService service = new UserService(users, mock(CompanyService.class), mock(RoleService.class),
                mock(ResumeRepository.class), mock(PlanOrderRepository.class), mock(NotificationService.class));
        User body = new User();
        body.setId(1); // the seeded admin
        body.setEmail("attacker@example.com");

        service.handleCreateUser(body);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertEquals(0, saved.getValue().getId());
    }

    @Test
    void creatingACompanyNeverReplacesAnExistingRow() {
        CompanyRepository companies = mock(CompanyRepository.class);
        when(companies.save(any(Company.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CompanyService service = new CompanyService(companies, mock(UserRepository.class), mock(JobRepository.class),
                mock(NotificationService.class));
        Company body = new Company();
        body.setId(1);
        body.setName("Hijack");

        service.handleCreateCompany(body);

        ArgumentCaptor<Company> saved = ArgumentCaptor.forClass(Company.class);
        verify(companies).save(saved.capture());
        assertEquals(0, saved.getValue().getId());
    }
}
