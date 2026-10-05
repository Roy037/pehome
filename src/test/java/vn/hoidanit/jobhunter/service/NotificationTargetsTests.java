package vn.hoidanit.jobhunter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import vn.hoidanit.jobhunter.domain.Company;
import vn.hoidanit.jobhunter.domain.Job;
import vn.hoidanit.jobhunter.domain.Resume;
import vn.hoidanit.jobhunter.domain.Review;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.repository.PlanOrderRepository;
import vn.hoidanit.jobhunter.repository.UserRepository;
import vn.hoidanit.jobhunter.service.EmailService.Notice;

// Who gets which e-mail, and what the notice says: the parts that are logic rather than wording.
class NotificationTargetsTests {
    EmailService mail = mock(EmailService.class);
    UserRepository users = mock(UserRepository.class);
    NotificationService notifications = new NotificationService(mail, users, mock(PlanOrderRepository.class));
    Company company;
    User verifiedHr;
    User unverifiedHr;

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setId(5);
        company.setName("Acme");
        verifiedHr = hr("verified@acme.com", true);
        unverifiedHr = hr("stranger@elsewhere.com", false);
        when(users.findByCompany(company)).thenReturn(List.of(verifiedHr, unverifiedHr));
    }

    User hr(String email, boolean verified) {
        User user = new User();
        user.setEmail(email);
        user.setName("HR");
        user.setEmailVerified(verified);
        return user;
    }

    Job job(String name) {
        Job job = new Job();
        job.setId(9);
        job.setName(name);
        job.setCompany(company);
        job.setLockReason("Nội dung sai sự thật");
        job.setEndDate(Instant.parse("2026-10-06T16:59:59Z"));
        return job;
    }

    @Test
    void employerMailsSkipAddressesThatNeverProvedTheyAreTheirs() {
        notifications.jobLocked(job("Tester"));
        verify(mail).sendNotice(eq("verified@acme.com"), any(), any());
        verify(mail, never()).sendNotice(eq("stranger@elsewhere.com"), any(), any());
    }

    @Test
    void aLockedPostMailCarriesTheReason() {
        notifications.jobLocked(job("Tester"));
        ArgumentCaptor<Notice> notice = ArgumentCaptor.forClass(Notice.class);
        verify(mail).sendNotice(any(), any(), notice.capture());
        assertEquals("Nội dung sai sự thật", notice.getValue().note());
        assertEquals("Lý do khóa", notice.getValue().noteTitle());
    }

    @Test
    void accountLockedMailHasNoButton() {
        User user = hr("a@example.com", true);
        notifications.accountLocked(user);
        ArgumentCaptor<Notice> notice = ArgumentCaptor.forClass(Notice.class);
        verify(mail).sendNotice(eq("a@example.com"), any(), notice.capture());
        assertNull(notice.getValue().ctaPath());
        notifications.accountUnlocked(user);
        verify(mail).sendNotice(eq("a@example.com"), eq("Tài khoản itjobs của bạn đã được mở khóa"), any());
    }

    @Test
    void theInterviewReminderGoesToTheCandidateAndTheVerifiedEmployerWithTheMeetingLink() {
        Resume resume = new Resume();
        resume.setEmail("cand@example.com");
        resume.setJob(job("Tester"));
        resume.setInterviewAt(Instant.parse("2026-10-06T02:00:00Z"));
        resume.setMeetingLink("https://meet.google.com/abc-defg-hij");
        notifications.interviewTomorrow(resume);

        ArgumentCaptor<Notice> toCandidate = ArgumentCaptor.forClass(Notice.class);
        verify(mail).sendNotice(eq("cand@example.com"), any(), toCandidate.capture());
        assertEquals("https://meet.google.com/abc-defg-hij", toCandidate.getValue().ctaPath());
        assertEquals("09:00 06/10/2026", toCandidate.getValue().details().get("Thời gian"), "shown in Vietnam time");
        ArgumentCaptor<Notice> toHr = ArgumentCaptor.forClass(Notice.class);
        verify(mail).sendNotice(eq("verified@acme.com"), any(), toHr.capture());
        assertTrue(toHr.getValue().details().containsKey("Ứng viên"));
        verify(mail, never()).sendNotice(eq("stranger@elsewhere.com"), any(), any());
    }

    @Test
    void theDigestCountsApplicationsPerPost() {
        Resume a = new Resume();
        a.setJob(job("Tester"));
        Resume b = new Resume();
        b.setJob(job("Tester"));
        Resume c = new Resume();
        c.setJob(job("Dev"));
        notifications.hrDailyDigest(company, List.of(a, b, c));
        ArgumentCaptor<Notice> notice = ArgumentCaptor.forClass(Notice.class);
        verify(mail).sendNotice(eq("verified@acme.com"), eq("3 hồ sơ ứng tuyển mới – Acme"), notice.capture());
        assertEquals("2 hồ sơ", notice.getValue().details().get("Tester"));
        assertEquals("1 hồ sơ", notice.getValue().details().get("Dev"));
    }

    @Test
    void aNewReviewTellsTheCompanyTheScoreAndTheText() {
        Review review = new Review();
        review.setCompany(company);
        review.setUser(hr("rev@example.com", true));
        review.setRating(4);
        review.setContent("Môi trường tốt.");
        notifications.reviewReceived(review);
        ArgumentCaptor<Notice> notice = ArgumentCaptor.forClass(Notice.class);
        verify(mail).sendNotice(eq("verified@acme.com"), eq("Đánh giá mới cho Acme – 4/5"), notice.capture());
        assertEquals("4 / 5", notice.getValue().details().get("Điểm"));
        assertEquals("Môi trường tốt.", notice.getValue().note());
    }
}
