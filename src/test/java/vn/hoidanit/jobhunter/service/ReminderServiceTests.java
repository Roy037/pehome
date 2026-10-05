package vn.hoidanit.jobhunter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import vn.hoidanit.jobhunter.domain.Company;
import vn.hoidanit.jobhunter.domain.Job;
import vn.hoidanit.jobhunter.domain.Resume;
import vn.hoidanit.jobhunter.domain.SavedJob;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.repository.JobRepository;
import vn.hoidanit.jobhunter.repository.ResumeRepository;
import vn.hoidanit.jobhunter.repository.SavedJobRepository;

class ReminderServiceTests {
    JobRepository jobs = mock(JobRepository.class);
    SavedJobRepository saved = mock(SavedJobRepository.class);
    ResumeRepository resumes = mock(ResumeRepository.class);
    NotificationService notifications = mock(NotificationService.class);
    ReminderService service = new ReminderService(jobs, saved, resumes, notifications);

    // 2026-10-05 10:17:45 UTC
    final Instant now = Instant.parse("2026-10-05T10:17:45Z");

    Company company(long id) {
        Company company = new Company();
        company.setId(id);
        company.setName("Company " + id);
        return company;
    }

    Job job(long id, Company company) {
        Job job = new Job();
        job.setId(id);
        job.setName("Job " + id);
        job.setCompany(company);
        return job;
    }

    @Test
    void savedJobsGoOutOncePerCandidateWithEveryClosingJob() {
        Company acme = company(1);
        User ann = new User();
        ann.setId(1);
        User bob = new User();
        bob.setId(2);
        SavedJob a1 = new SavedJob();
        a1.setUser(ann);
        a1.setJob(job(10, acme));
        SavedJob a2 = new SavedJob();
        a2.setUser(ann);
        a2.setJob(job(11, acme));
        SavedJob b1 = new SavedJob();
        b1.setUser(bob);
        b1.setJob(job(10, acme));
        when(saved.findClosingBetween(any(), any())).thenReturn(List.of(a1, a2, b1));

        assertEquals(2, service.savedJobsClosing(now));

        verify(saved).findClosingBetween(now.plusSeconds(24 * 3600), now.plusSeconds(48 * 3600));
        ArgumentCaptor<List<Job>> annsJobs = ArgumentCaptor.forClass(List.class);
        verify(notifications).savedJobsClosing(eq(ann), annsJobs.capture());
        assertEquals(2, annsJobs.getValue().size());
        verify(notifications).savedJobsClosing(eq(bob), any());
    }

    @Test
    void employersGetOneMailPerCompanyForItsClosingPosts() {
        Company acme = company(1);
        Company beta = company(2);
        when(jobs.findEndingBetween(any(), any())).thenReturn(List.of(job(1, acme), job(2, acme), job(3, beta)));

        assertEquals(2, service.jobsExpiring(now));

        ArgumentCaptor<List<Job>> acmeJobs = ArgumentCaptor.forClass(List.class);
        verify(notifications).jobsExpiring(eq(acme), acmeJobs.capture());
        assertEquals(2, acmeJobs.getValue().size());
        verify(notifications).jobsExpiring(eq(beta), any());
    }

    @Test
    void interviewWindowIsTheHourThatStartsTwentyThreeHoursAfterTheTopOfThisHour() {
        Resume interview = new Resume();
        when(resumes.findInterviewsBetween(any(), any())).thenReturn(List.of(interview));

        assertEquals(1, service.interviewsTomorrow(now));

        // run at 10:17 -> top of the hour 10:00 -> interviews from tomorrow 09:00 up to (not including) tomorrow 10:00
        verify(resumes).findInterviewsBetween(Instant.parse("2026-10-06T09:00:00Z"), Instant.parse("2026-10-06T10:00:00Z"));
        verify(notifications).interviewTomorrow(interview);
    }

    @Test
    void theMorningDigestCoversTheLastTwentyFourHoursPerCompany() {
        Company acme = company(1);
        Company beta = company(2);
        Resume r1 = new Resume();
        r1.setJob(job(1, acme));
        Resume r2 = new Resume();
        r2.setJob(job(2, acme));
        Resume r3 = new Resume();
        r3.setJob(job(3, beta));
        when(resumes.findReceivedBetween(any(), any())).thenReturn(List.of(r1, r2, r3));

        assertEquals(2, service.hrDailyDigest(now));

        verify(resumes).findReceivedBetween(now.minusSeconds(24 * 3600), now);
        verify(notifications, times(2)).hrDailyDigest(any(), any());
    }
}
