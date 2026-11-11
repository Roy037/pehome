package vn.hoidanit.jobhunter.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import vn.hoidanit.jobhunter.domain.Company;
import vn.hoidanit.jobhunter.domain.CompanyFollow;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.repository.CompanyFollowRepository;
import vn.hoidanit.jobhunter.repository.CompanyRepository;
import vn.hoidanit.jobhunter.util.error.ResourceNotFoundException;

class CompanyFollowTests {
    CompanyFollowRepository follows = mock(CompanyFollowRepository.class);
    CompanyRepository companies = mock(CompanyRepository.class);
    UserService users = mock(UserService.class);
    CompanyFollowService service = new CompanyFollowService(follows, companies, users);
    Company company = new Company();

    @BeforeEach
    void setUp() throws Exception {
        User candidate = new User();
        candidate.setId(7);
        company.setId(3);
        company.setApproved(true);
        when(users.currentCandidate()).thenReturn(candidate);
        when(users.handleGetCurrentUser()).thenReturn(candidate);
        when(companies.findById(3L)).thenReturn(Optional.of(company));
    }

    @Test
    void followingTwiceOrTwoQuickTapsLeaveOneFollow() throws Exception {
        when(follows.existsByUserIdAndCompanyId(7L, 3L)).thenReturn(true);
        service.follow(3);
        verify(follows, never()).save(any());

        when(follows.existsByUserIdAndCompanyId(7L, 3L)).thenReturn(false);
        when(follows.save(any(CompanyFollow.class))).thenThrow(new DataIntegrityViolationException("uk_follow"));
        assertDoesNotThrow(() -> service.follow(3));
    }

    @Test
    void onlyApprovedCompaniesCanBeFollowed() {
        company.setApproved(false);

        assertThrows(ResourceNotFoundException.class, () -> service.follow(3));
    }

    @Test
    void aCompanyThatLosesItsApprovalDropsOutOfMyList() throws Exception {
        Company pending = new Company();
        pending.setApproved(false);
        CompanyFollow a = new CompanyFollow();
        a.setCompany(company);
        CompanyFollow b = new CompanyFollow();
        b.setCompany(pending);
        when(follows.findByUserIdOrderByCreatedAtDesc(7L)).thenReturn(List.of(a, b));

        assertEquals(List.of(company), service.fetchMine());
    }
}
