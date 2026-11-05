package vn.hoidanit.jobhunter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import vn.hoidanit.jobhunter.domain.Company;
import vn.hoidanit.jobhunter.domain.Job;
import vn.hoidanit.jobhunter.domain.Resume;
import vn.hoidanit.jobhunter.domain.Role;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.domain.request.ReqResumeEvaluationDTO;
import vn.hoidanit.jobhunter.domain.request.ReqResumeStatusDTO;
import vn.hoidanit.jobhunter.domain.response.resume.ResFetchResumeDTO;
import vn.hoidanit.jobhunter.repository.JobRepository;
import vn.hoidanit.jobhunter.repository.ResumeRepository;
import vn.hoidanit.jobhunter.repository.UserRepository;
import vn.hoidanit.jobhunter.util.constant.ResumeStateEnum;
import vn.hoidanit.jobhunter.util.error.ConflictException;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;
import vn.hoidanit.jobhunter.util.error.PermissionException;

class ApplicationPipelineTests {
    ResumeRepository resumes = mock(ResumeRepository.class);
    UserService users = mock(UserService.class);
    EmailService email = mock(EmailService.class);
    ResumeService service = new ResumeService(resumes, mock(UserRepository.class), mock(JobRepository.class), users,
            email, mock(FileService.class), mock(PlanService.class), mock(NotificationService.class));
    Company hiring = company(3);
    User hr = new User();
    Resume resume = new Resume();

    static Company company(long id) {
        Company company = new Company();
        company.setId(id);
        company.setName("VINFAST");
        return company;
    }

    static User superAdmin() {
        Role role = new Role();
        role.setName("SUPER_ADMIN");
        User admin = new User();
        admin.setId(1);
        admin.setRole(role);
        return admin;
    }

    @BeforeEach
    void setUp() throws Exception {
        Role role = new Role();
        role.setName("HR");
        hr.setId(20);
        hr.setRole(role);
        hr.setCompany(hiring);
        when(users.handleGetCurrentUser()).thenReturn(hr);
        when(users.currentUserCompany()).thenReturn(hiring);

        Job job = new Job();
        job.setId(40);
        job.setName("Kỹ sư kiểm thử");
        job.setCompany(hiring);
        User candidate = new User();
        candidate.setId(7);
        candidate.setName("An");
        resume.setId(100);
        resume.setJob(job);
        resume.setUser(candidate);
        resume.setEmail("an@example.com");
        resume.setStatus(ResumeStateEnum.PENDING);
        when(resumes.findById(100L)).thenReturn(Optional.of(resume));
        when(resumes.save(any(Resume.class))).thenAnswer(call -> call.getArgument(0));
    }

    static ReqResumeStatusDTO move(ResumeStateEnum to) {
        ReqResumeStatusDTO req = new ReqResumeStatusDTO();
        req.setStatus(to);
        req.setNotify(false);
        return req;
    }

    @Test
    void anEmployerOnlyMovesAnApplicationForward() throws Exception {
        assertEquals(ResumeStateEnum.SHORTLISTED, service.changeStatus(100, move(ResumeStateEnum.SHORTLISTED)).getStatus());

        assertThrows(ConflictException.class, () -> service.changeStatus(100, move(ResumeStateEnum.PENDING)));
        ConflictException same = assertThrows(ConflictException.class,
                () -> service.changeStatus(100, move(ResumeStateEnum.SHORTLISTED)));
        assertEquals("Hồ sơ đã ở trạng thái này rồi.", same.getMessage());
    }

    @Test
    void acceptedAndRejectedAreFinalForAnEmployer() {
        for (ResumeStateEnum end : new ResumeStateEnum[] { ResumeStateEnum.ACCEPTED, ResumeStateEnum.REJECTED }) {
            resume.setStatus(end);
            for (ResumeStateEnum to : ResumeStateEnum.values()) {
                assertThrows(ConflictException.class, () -> service.changeStatus(100, move(to)), end + " -> " + to);
            }
        }
    }

    @Test
    void aSuperAdminMayCorrectAnyStatus() throws Exception {
        when(users.handleGetCurrentUser()).thenReturn(superAdmin());
        when(users.currentUserCompany()).thenReturn(null);
        resume.setStatus(ResumeStateEnum.REJECTED);

        assertEquals(ResumeStateEnum.PENDING, service.changeStatus(100, move(ResumeStateEnum.PENDING)).getStatus());
    }

    @Test
    void anInterviewNeedsAFutureTimeAndAMeetingLink() throws Exception {
        resume.setStatus(ResumeStateEnum.SHORTLISTED);
        ReqResumeStatusDTO req = move(ResumeStateEnum.INTERVIEW);
        req.setInterviewAt(Instant.now().minus(Duration.ofHours(1)));
        req.setMeetingLink("https://meet.google.com/abc-defg-hij");
        assertThrows(IdInvalidException.class, () -> service.changeStatus(100, req));

        req.setInterviewAt(Instant.now().plus(Duration.ofDays(2)));
        req.setMeetingLink("   ");
        assertThrows(IdInvalidException.class, () -> service.changeStatus(100, req));
        assertEquals(ResumeStateEnum.SHORTLISTED, resume.getStatus());

        req.setMeetingLink("  https://meet.google.com/abc-defg-hij  ");
        Resume saved = service.changeStatus(100, req);
        assertEquals(ResumeStateEnum.INTERVIEW, saved.getStatus());
        assertEquals("https://meet.google.com/abc-defg-hij", saved.getMeetingLink());
    }

    @Test
    void movingAnInterviewAgainReschedulesItAndTellsTheCandidate() throws Exception {
        resume.setStatus(ResumeStateEnum.INTERVIEW);
        ReqResumeStatusDTO req = move(ResumeStateEnum.INTERVIEW);
        req.setInterviewAt(Instant.now().plus(Duration.ofDays(3)));
        req.setMeetingLink("https://meet.google.com/xyz");
        req.setNotify(true);

        service.changeStatus(100, req);

        verify(email).sendTemplate(eq("an@example.com"), startsWith("Cập nhật lịch phỏng vấn"), eq("interview-invitation"),
                anyMap());
    }

    @Test
    void noMailGoesOutWhenTheEmployerTurnsNotifyOff() throws Exception {
        service.changeStatus(100, move(ResumeStateEnum.SHORTLISTED));

        verify(email, never()).sendTemplate(anyString(), anyString(), anyString(), anyMap());
    }

    @Test
    void anotherCompanysEmployerCannotMoveOrScoreTheApplication() {
        when(users.currentUserCompany()).thenReturn(company(9));
        ReqResumeEvaluationDTO score = new ReqResumeEvaluationDTO();
        score.setScore(8);

        assertThrows(PermissionException.class, () -> service.changeStatus(100, move(ResumeStateEnum.SHORTLISTED)));
        assertThrows(PermissionException.class, () -> service.evaluate(100, score));
        verify(resumes, never()).save(any());
    }

    @Test
    void theCandidateNeverSeesTheEmployersScoreOrRemark() throws Exception {
        ReqResumeEvaluationDTO score = new ReqResumeEvaluationDTO();
        score.setScore(8);
        score.setRemark("  Mạnh về SQL  ");

        service.evaluate(100, score);

        assertEquals("Mạnh về SQL", resume.getRemark());
        assertEquals(Integer.valueOf(8), service.getResume(resume).getScore());
        ResFetchResumeDTO own = service.getResumeForCandidate(resume);
        assertNull(own.getScore());
        assertNull(own.getRemark());
    }

    @Test
    void onlyTheApplicantTheHiringCompanyOrAnAdminCanOpenTheCv() {
        User otherEmployer = new User();
        otherEmployer.setId(50);
        otherEmployer.setCompany(company(9));
        User stranger = new User();
        stranger.setId(51);

        assertTrue(service.canViewDocument(resume, resume.getUser()));
        assertTrue(service.canViewDocument(resume, hr));
        assertTrue(service.canViewDocument(resume, superAdmin()));
        assertFalse(service.canViewDocument(resume, otherEmployer));
        assertFalse(service.canViewDocument(resume, stranger));
    }
}
