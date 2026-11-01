package vn.hoidanit.jobhunter.service;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import vn.hoidanit.jobhunter.domain.Company;
import vn.hoidanit.jobhunter.domain.CompanyFollow;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.repository.CompanyFollowRepository;
import vn.hoidanit.jobhunter.repository.CompanyRepository;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;
import vn.hoidanit.jobhunter.util.error.PermissionException;
import vn.hoidanit.jobhunter.util.error.ResourceNotFoundException;

@Service
public class CompanyFollowService {

    private final CompanyFollowRepository followRepository;
    private final CompanyRepository companyRepository;
    private final UserService userService;

    public CompanyFollowService(CompanyFollowRepository followRepository, CompanyRepository companyRepository,
            UserService userService) {
        this.followRepository = followRepository;
        this.companyRepository = companyRepository;
        this.userService = userService;
    }

    public List<Company> fetchMine() throws IdInvalidException {
        User user = this.userService.handleGetCurrentUser();
        return this.followRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream().map(CompanyFollow::getCompany).filter(Company::isApproved).toList();
    }

    public void follow(long companyId) throws IdInvalidException, PermissionException {
        User user = this.userService.currentCandidate();
        Company company = this.companyRepository.findById(companyId)
                .filter(Company::isApproved)
                .orElseThrow(() -> new ResourceNotFoundException("Công ty với id = " + companyId + " không tồn tại"));
        if (this.followRepository.existsByUserIdAndCompanyId(user.getId(), companyId)) {
            return;
        }
        CompanyFollow follow = new CompanyFollow();
        follow.setUser(user);
        follow.setCompany(company);
        try {
            this.followRepository.save(follow);
        } catch (DataIntegrityViolationException e) {
            // two quick taps raced past the exists check; the unique key already guarantees one row
        }
    }

    public void unfollow(long companyId) throws IdInvalidException {
        User user = this.userService.handleGetCurrentUser();
        this.followRepository.deleteByUserIdAndCompanyId(user.getId(), companyId);
    }
}
