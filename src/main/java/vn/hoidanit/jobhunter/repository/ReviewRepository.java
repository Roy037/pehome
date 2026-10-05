package vn.hoidanit.jobhunter.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import vn.hoidanit.jobhunter.domain.Review;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long>, JpaSpecificationExecutor<Review> {
    Page<Review> findByCompanyId(long companyId, Pageable pageable);

    Optional<Review> findByUserIdAndCompanyId(long userId, long companyId);

    @Query("select r.rating, count(r) from Review r where r.company.id = :companyId group by r.rating")
    List<Object[]> countByRating(@Param("companyId") long companyId);
}
