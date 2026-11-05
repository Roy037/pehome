package vn.hoidanit.jobhunter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import vn.hoidanit.jobhunter.domain.Company;
import vn.hoidanit.jobhunter.domain.Job;
import vn.hoidanit.jobhunter.domain.Resume;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.repository.JobRepository;
import vn.hoidanit.jobhunter.repository.ResumeRepository;
import vn.hoidanit.jobhunter.repository.UserRepository;
import vn.hoidanit.jobhunter.util.error.ConflictException;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;

class ApplyRulesTests {
    static final String OWN_FILE = "1790000000000-u7-cv.pdf";

    ResumeRepository resumes = mock(ResumeRepository.class);
    JobRepository jobs = mock(JobRepository.class);
    FileService files = mock(FileService.class);
    NotificationService notifications = mock(NotificationService.class);
    ResumeService service = new ResumeService(resumes, mock(UserRepository.class), jobs, mock(UserService.class),
            mock(EmailService.class), files, mock(PlanService.class), notifications);
    User candidate = new User();
    Job job = new Job();

    @BeforeEach
    void setUp() throws Exception {
        candidate.setId(7);
        Company company = new Company();
        company.setId(3);
        company.setApproved(true);
        job.setId(40);
        job.setActive(true);
        job.setCompany(company);
        job.setStartDate(Instant.now().minus(Duration.ofDays(1)));
        job.setEndDate(Instant.now().plus(Duration.ofDays(30)));
        when(jobs.findById(40L)).thenReturn(Optional.of(job));
        when(files.getFileLength(anyString(), eq("resume"))).thenReturn(1024L);
        when(resumes.saveAndFlush(any(Resume.class))).thenAnswer(call -> call.getArgument(0));
    }

    Resume application(String url) {
        Job ref = new Job();
        ref.setId(40);
        Resume resume = new Resume();
        resume.setUser(candidate);
        resume.setJob(ref);
        resume.setUrl(url);
        return resume;
    }

    @Test
    void theApplicantsOwnUploadedFileIsAcceptedAndTheEmployerIsTold() throws Exception {
        Resume resume = application(OWN_FILE);
        resume.setCoverLetter("  Em xin ứng tuyển vị trí này  ");

        service.create(resume);

        ArgumentCaptor<Resume> saved = ArgumentCaptor.forClass(Resume.class);
        verify(resumes).saveAndFlush(saved.capture());
        assertEquals("Em xin ứng tuyển vị trí này", saved.getValue().getCoverLetter());
        verify(notifications).applicationSent(saved.getValue(), job);
    }

    @Test
    void aBlankCoverLetterIsStoredAsNone() throws Exception {
        Resume resume = application(OWN_FILE);
        resume.setCoverLetter("   ");

        service.create(resume);

        assertNull(resume.getCoverLetter());
    }

    @Test
    void someoneElsesFileOrAFileMissingOnDiskIsRefused() throws Exception {
        assertThrows(IdInvalidException.class, () -> service.create(application("1790000000000-u8-cv.pdf")));
        assertThrows(IdInvalidException.class, () -> service.create(application("../u7-cv.pdf")));

        when(files.getFileLength(anyString(), eq("resume"))).thenReturn(0L);
        IdInvalidException missing = assertThrows(IdInvalidException.class, () -> service.create(application(OWN_FILE)));
        assertEquals("Không tìm thấy tệp CV, vui lòng tải lên lại.", missing.getMessage());
        verify(resumes, never()).saveAndFlush(any());
    }

    @Test
    void onlyHttpsDriveDropboxOrOneDriveLinksCountAsACv() throws Exception {
        assertTrue(ResumeService.isCloudLink("https://drive.google.com/file/d/abc/view"));
        assertTrue(ResumeService.isCloudLink("https://www.dropbox.com/s/abc/cv.pdf"));
        assertTrue(ResumeService.isCloudLink("https://company.sharepoint.com/cv.pdf"));
        assertFalse(ResumeService.isCloudLink("http://drive.google.com/file/d/abc"));
        assertFalse(ResumeService.isCloudLink("https://drive.google.com.evil.example/cv.pdf"));
        assertFalse(ResumeService.isCloudLink("https://user:secret@drive.google.com/cv.pdf"));
        assertFalse(ResumeService.isCloudLink("https://evil.example/drive.google.com"));

        service.create(application("https://drive.google.com/file/d/abc/view"));
        assertThrows(IdInvalidException.class, () -> service.create(application("https://evil.example/cv.pdf")));
    }

    @Test
    void aLockedClosedExpiredOrNotYetOpenJobTakesNoApplications() {
        job.setLocked(true);
        assertThrows(IdInvalidException.class, () -> service.create(application(OWN_FILE)));

        job.setLocked(false);
        job.setActive(false);
        assertThrows(IdInvalidException.class, () -> service.create(application(OWN_FILE)));

        job.setActive(true);
        job.setEndDate(Instant.now().minus(Duration.ofMinutes(1)));
        assertThrows(IdInvalidException.class, () -> service.create(application(OWN_FILE)));

        job.setEndDate(Instant.now().plus(Duration.ofDays(30)));
        job.setStartDate(Instant.now().plus(Duration.ofDays(2)));
        IdInvalidException early = assertThrows(IdInvalidException.class, () -> service.create(application(OWN_FILE)));
        assertEquals("Tin tuyển dụng này chưa mở nhận hồ sơ.", early.getMessage());

        verify(resumes, never()).saveAndFlush(any());
    }

    @Test
    void aCompanyWaitingForApprovalTakesNoApplications() {
        job.getCompany().setApproved(false);

        IdInvalidException e = assertThrows(IdInvalidException.class, () -> service.create(application(OWN_FILE)));

        assertEquals("Công ty này đang chờ xác minh nên chưa nhận hồ sơ.", e.getMessage());
    }

    @Test
    void applyingTwiceIsRefusedEvenWhenTwoRequestsRace() {
        when(resumes.existsByUserIdAndJobId(7L, 40L)).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.create(application(OWN_FILE)));

        when(resumes.existsByUserIdAndJobId(7L, 40L)).thenReturn(false);
        when(resumes.saveAndFlush(any(Resume.class))).thenThrow(new DataIntegrityViolationException("uk_resume_user_job"));
        assertThrows(ConflictException.class, () -> service.create(application(OWN_FILE)));

        verify(notifications, never()).applicationSent(any(), any());
    }
}
