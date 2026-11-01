package vn.hoidanit.jobhunter.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import vn.hoidanit.jobhunter.domain.Company;
import vn.hoidanit.jobhunter.domain.Job;
import vn.hoidanit.jobhunter.domain.JobReport;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.repository.PlanOrderRepository;
import vn.hoidanit.jobhunter.repository.UserRepository;
import vn.hoidanit.jobhunter.util.constant.JobReportReasonEnum;

class AdminAlertTests {
    EmailService mail = mock(EmailService.class);
    NotificationService notifications = new NotificationService(mail, mock(UserRepository.class), mock(PlanOrderRepository.class));

    Company company() {
        Company company = new Company();
        company.setName("Acme");
        company.setAddress("Hà Nội");
        return company;
    }

    JobReport report() {
        Job job = new Job();
        job.setName("Tester");
        job.setCompany(company());
        User reporter = new User();
        reporter.setEmail("c@example.com");
        JobReport report = new JobReport();
        report.setJob(job);
        report.setUser(reporter);
        report.setReason(JobReportReasonEnum.SCAM);
        return report;
    }

    @Test
    void nobodyIsMailedUnlessAnAdminAddressIsConfigured() {
        notifications.adminCompanyPending(company(), false);
        notifications.adminJobReported(report());
        verify(mail, never()).sendNotice(any(), any(), any());
    }

    @Test
    void everyConfiguredAddressGetsTheAlert() {
        ReflectionTestUtils.setField(notifications, "adminTo", " boss@example.com , ops@example.com ,, ");
        notifications.adminCompanyPending(company(), false);
        verify(mail).sendNotice(eq("boss@example.com"), eq("Công ty chờ duyệt: Acme"), any());
        verify(mail).sendNotice(eq("ops@example.com"), eq("Công ty chờ duyệt: Acme"), any());

        notifications.adminJobReported(report());
        verify(mail, times(2)).sendNotice(any(), eq("Báo cáo tin: Tester"), any());
    }
}
