package vn.hoidanit.jobhunter.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import vn.hoidanit.jobhunter.domain.Resume;

@Repository
public interface ResumeRepository extends JpaRepository<Resume, Long>,
        JpaSpecificationExecutor<Resume> {
    boolean existsByUserIdAndJobId(long userId, long jobId);

    boolean existsByJobId(long jobId);

    boolean existsByUserId(long userId);

    // Interviews that start inside [from, to): the day-before reminder.
    @Query("select r from Resume r join fetch r.job j join fetch j.company where r.status = 'INTERVIEW' "
            + "and r.interviewAt >= :from and r.interviewAt < :to and r.meetingLink is not null")
    List<Resume> findInterviewsBetween(@Param("from") Instant from, @Param("to") Instant to);

    // Applications received inside [from, to), grouped by company in the employers' morning digest.
    @Query("select r from Resume r join fetch r.job j join fetch j.company c where r.createdAt >= :from and r.createdAt < :to "
            + "order by c.id, j.id, r.createdAt")
    List<Resume> findReceivedBetween(@Param("from") Instant from, @Param("to") Instant to);
}
