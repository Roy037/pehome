package vn.hoidanit.jobhunter.service;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import vn.hoidanit.jobhunter.domain.Company;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.domain.response.Meta;
import vn.hoidanit.jobhunter.domain.response.ResultPaginationDTO;
import vn.hoidanit.jobhunter.repository.CompanyRepository;

@Service
public class CompanyService {
    private final CompanyRepository companyRepository;

    public CompanyService(
            CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;

    }

    public void handleDeleteCompany(long id) {
        this.companyRepository.deleteById(id);
    }

    public Optional<Company> findById(long id) {
        return this.companyRepository.findById(id);
    }

    public Company handleCreateCompany(Company company) {
        return this.companyRepository.save(company);
    }

    // public List<Company> handleGetCompany() {
    // return this.companyRepository.findAll();
    // }

    public Company handleUpdateCompany(Company reqCompany) {

        Optional<Company> companyOptional = this.companyRepository.findById(reqCompany.getId());
        if (companyOptional.isPresent()) {
            Company currentCompany = companyOptional.get();
            currentCompany.setLogo(reqCompany.getLogo());
            currentCompany.setName(reqCompany.getName());
            currentCompany.setDescription(reqCompany.getDescription());
            currentCompany.setAddress(reqCompany.getAddress());

            // save
            return this.companyRepository.save(currentCompany);
        }
        return null;
    }

    public ResultPaginationDTO handleGetCompany(Pageable pageable) {

        Page<Company> pageCompany = this.companyRepository.findAll(pageable);
        ResultPaginationDTO rs = new ResultPaginationDTO();
        Meta mt = new Meta();

        mt.setPage(pageable.getPageNumber() + 1);
        mt.setPageSize(pageable.getPageSize());

        mt.setPages(pageCompany.getTotalPages());
        mt.setTotal(pageCompany.getTotalElements());

        rs.setMeta(mt);
        rs.setResult(pageCompany.getContent());
        return rs;
    }

}
