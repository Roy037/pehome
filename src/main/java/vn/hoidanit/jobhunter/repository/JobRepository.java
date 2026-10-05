package vn.hoidanit.jobhunter.repository;

import java.util.List;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import vn.hoidanit.jobhunter.domain.Job;
import vn.hoidanit.jobhunter.domain.Skill;

@Repository
public interface JobRepository extends JpaRepository<Job, Long>,
        JpaSpecificationExecutor<Job> {

    List<Job> findBySkillsIn(List<Skill> skills);

    boolean existsByCompanyId(long companyId);

    // Jobs that count against a company's places for open jobs: switched on, not locked, not past the deadline.
    @Query("select count(j) from Job j where j.company.id = :companyId and j.id <> :exceptId and j.active = true "
            + "and j.locked = false and (j.endDate is null or j.endDate > :now)")
    long countOpen(@Param("companyId") long companyId, @Param("exceptId") long exceptId, @Param("now") Instant now);

    @Query("select j from Job j where j.company.id = :companyId and j.pinnedUntil > :now order by j.pinnedUntil")
    List<Job> findPinned(@Param("companyId") long companyId, @Param("now") Instant now);

    @Query("select j.pinnedUntil from Job j where j.id = :id")
    Instant pinnedUntilOf(@Param("id") long id);

    // updated directly: the paid order changes the pin without going through the entity (and its audit fields)
    @Modifying
    @Transactional
    @Query("update Job j set j.pinnedUntil = :until where j.id = :id")
    int pin(@Param("id") long id, @Param("until") Instant until);

    @Modifying
    @Transactional
    @Query("update Job j set j.pinnedUntil = null where j.pinnedUntil <= :now")
    int clearExpiredPins(@Param("now") Instant now);

    // Open postings that share at least one of the skills and were published after `since`, newest first.
    @Query("select distinct j from Job j join j.skills s where s in :skills and j.active = true and j.locked = false "
            + "and j.company.approved = true and (j.endDate is null or j.endDate > :now) and (j.startDate is null or j.startDate <= :now) "
            + "and j.createdAt >= :since order by j.createdAt desc")
    List<Job> findAlertJobs(@Param("skills") List<Skill> skills, @Param("now") Instant now,
            @Param("since") Instant since, Pageable pageable);

    // Posts that close inside [from, to): the employers get a heads-up before they stop taking applications.
    @Query("select j from Job j join fetch j.company c where j.active = true and j.locked = false and c.approved = true "
            + "and j.endDate >= :from and j.endDate < :to order by c.id, j.endDate")
    List<Job> findEndingBetween(@Param("from") Instant from, @Param("to") Instant to);

    // Open postings of approved companies that share skills with a job (most shared first), minus that job and anything the
    // user already applied to.
    @Query("select j from Job j join j.skills s where s in :skills and j.id <> :jobId and j.active = true "
            + "and j.locked = false and j.company.approved = true "
            + "and (j.endDate is null or j.endDate > :now) and (j.startDate is null or j.startDate <= :now) "
            + "and not exists (select r.id from Resume r where r.job = j and r.user.id = :userId) "
            + "group by j order by count(s) desc, j.createdAt desc")
    List<Job> findSimilarOpenJobs(@Param("skills") List<Skill> skills, @Param("jobId") long jobId,
            @Param("userId") long userId, @Param("now") Instant now, Pageable pageable);
}