package vn.hoidanit.jobhunter.service;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

import vn.hoidanit.jobhunter.domain.Company;
import vn.hoidanit.jobhunter.repository.CompanyRepository;

@Service
public class CompanyService {
    private final CompanyRepository companyRepository;

    public CompanyService(
            CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;

    }

    public Company handleCreateCompany(Company company) {
        return this.companyRepository.save(company);
    }
}
