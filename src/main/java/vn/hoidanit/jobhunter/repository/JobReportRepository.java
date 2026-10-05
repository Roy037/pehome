package vn.hoidanit.jobhunter.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import vn.hoidanit.jobhunter.domain.JobReport;

@Repository
public interface JobReportRepository extends JpaRepository<JobReport, Long>, JpaSpecificationExecutor<JobReport> {
    boolean existsByUserIdAndJobId(long userId, long jobId);
}
