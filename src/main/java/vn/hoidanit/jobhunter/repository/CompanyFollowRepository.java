package vn.hoidanit.jobhunter.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import vn.hoidanit.jobhunter.domain.CompanyFollow;

@Repository
public interface CompanyFollowRepository extends JpaRepository<CompanyFollow, Long> {
    List<CompanyFollow> findByUserIdOrderByCreatedAtDesc(long userId);

    boolean existsByUserIdAndCompanyId(long userId, long companyId);

    @Transactional
    long deleteByUserIdAndCompanyId(long userId, long companyId);
}
