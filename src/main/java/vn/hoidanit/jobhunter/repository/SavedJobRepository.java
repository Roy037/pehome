package vn.hoidanit.jobhunter.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import vn.hoidanit.jobhunter.domain.SavedJob;

@Repository
public interface SavedJobRepository extends JpaRepository<SavedJob, Long>, JpaSpecificationExecutor<SavedJob> {
    List<SavedJob> findByUserIdOrderByCreatedAtDesc(long userId);

    boolean existsByUserIdAndJobId(long userId, long jobId);

    long countByUserId(long userId);

    @Transactional
    long deleteByUserIdAndJobId(long userId, long jobId);

    // Saved posts that close inside [from, to), for verified, active candidates who have not applied to them yet.
    @Query("select s from SavedJob s join fetch s.job j join fetch j.company c join fetch s.user u "
            + "where j.active = true and j.locked = false and c.approved = true and j.endDate >= :from and j.endDate < :to "
            + "and u.emailVerified = true and u.locked = false "
            + "and not exists (select r.id from Resume r where r.job = j and r.user = u) order by u.id, j.endDate")
    List<SavedJob> findClosingBetween(@Param("from") Instant from, @Param("to") Instant to);
}
