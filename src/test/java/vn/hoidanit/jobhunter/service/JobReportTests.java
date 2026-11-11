package vn.hoidanit.jobhunter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import vn.hoidanit.jobhunter.domain.Job;
import vn.hoidanit.jobhunter.domain.JobReport;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.domain.request.ReqJobReportDTO;
import vn.hoidanit.jobhunter.repository.JobReportRepository;
import vn.hoidanit.jobhunter.repository.JobRepository;
import vn.hoidanit.jobhunter.util.constant.JobReportReasonEnum;
import vn.hoidanit.jobhunter.util.error.ConflictException;
import vn.hoidanit.jobhunter.util.error.ResourceNotFoundException;

class JobReportTests {
    JobReportRepository reports = mock(JobReportRepository.class);
    JobRepository jobs = mock(JobRepository.class);
    UserService users = mock(UserService.class);
    NotificationService notifications = mock(NotificationService.class);
    JobReportService service = new JobReportService(reports, jobs, users, notifications);
    Job job = new Job();

    @BeforeEach
    void setUp() throws Exception {
        User user = new User();
        user.setId(7);
        job.setId(40);
        when(users.handleGetCurrentUser()).thenReturn(user);
        when(jobs.findById(40L)).thenReturn(Optional.of(job));
    }

    static ReqJobReportDTO report(String detail) {
        ReqJobReportDTO req = new ReqJobReportDTO();
        req.setJobId(40L);
        req.setReason(JobReportReasonEnum.SCAM);
        req.setDetail(detail);
        return req;
    }

    JobReport savedReport() {
        ArgumentCaptor<JobReport> saved = ArgumentCaptor.forClass(JobReport.class);
        verify(reports).save(saved.capture());
        return saved.getValue();
    }

    @Test
    void aReportReachesTheAdminWithATrimmedDetail() throws Exception {
        service.report(report("  Yêu cầu nộp phí trước khi phỏng vấn  "));

        JobReport saved = savedReport();
        assertEquals("Yêu cầu nộp phí trước khi phỏng vấn", saved.getDetail());
        assertEquals(JobReportReasonEnum.SCAM, saved.getReason());
        verify(notifications).adminJobReported(saved);
    }

    @Test
    void aBlankDetailIsStoredAsNone() throws Exception {
        service.report(report("   "));

        assertNull(savedReport().getDetail());
    }

    @Test
    void theSamePersonCannotReportAJobTwiceEvenWhenTwoRequestsRace() {
        when(reports.existsByUserIdAndJobId(7L, 40L)).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.report(report(null)));

        when(reports.existsByUserIdAndJobId(7L, 40L)).thenReturn(false);
        when(reports.save(any(JobReport.class))).thenThrow(new DataIntegrityViolationException("uk_report_user_job"));
        assertThrows(ConflictException.class, () -> service.report(report(null)));

        verify(notifications, never()).adminJobReported(any());
    }

    @Test
    void dismissingAReportThatIsGoneIsNotFound() {
        when(reports.existsById(5L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> service.dismiss(5));
        verify(reports, never()).deleteById(any());
    }
}
