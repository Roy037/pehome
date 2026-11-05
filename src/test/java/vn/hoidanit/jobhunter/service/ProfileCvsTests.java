package vn.hoidanit.jobhunter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import jakarta.validation.Validation;
import vn.hoidanit.jobhunter.domain.CandidateProfile;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.domain.request.ReqProfileDTO;
import vn.hoidanit.jobhunter.domain.response.profile.ResProfileDTO;
import vn.hoidanit.jobhunter.repository.CandidateProfileRepository;
import vn.hoidanit.jobhunter.repository.SubscriberRepository;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;

class ProfileCvsTests {
    CandidateProfileRepository profiles = mock(CandidateProfileRepository.class);
    UserService users = mock(UserService.class);
    ProfileService service = new ProfileService(profiles, mock(SubscriberRepository.class), users);
    CandidateProfile profile = new CandidateProfile();

    @BeforeEach
    void setUp() throws Exception {
        User me = new User();
        me.setId(7);
        me.setEmail("a@example.com");
        when(users.handleGetCurrentUser()).thenReturn(me);
        when(profiles.findByUserId(7L)).thenReturn(Optional.of(profile));
        when(profiles.save(any(CandidateProfile.class))).thenAnswer(call -> call.getArgument(0));
    }

    static CandidateProfile.Cv cv(String url, String name) {
        CandidateProfile.Cv cv = new CandidateProfile.Cv();
        cv.setUrl(url);
        cv.setName(name);
        return cv;
    }

    @Test
    void threeUploadedFilesAreKeptInOrderWithTheirLabels() throws Exception {
        ReqProfileDTO req = new ReqProfileDTO();
        req.setCvs(List.of(cv("1-u7-en.pdf", "CV tiếng Anh"), cv("2-u7-vi.docx", "CV tiếng Việt"), cv("3-u7-ielts.png", " ")));

        ResProfileDTO res = service.saveMine(req);

        assertEquals(List.of("1-u7-en.pdf", "2-u7-vi.docx", "3-u7-ielts.png"), res.getCvs().stream().map(CandidateProfile.Cv::getUrl).toList());
        assertEquals("CV tiếng Việt", res.getCvs().get(1).getName());
        assertEquals(null, res.getCvs().get(2).getName(), "a blank label is stored as none");
        assertNotNull(res.getCvs().get(0).getUpdatedAt());
    }

    @Test
    void aFileSomeoneElseUploadedIsRefused() {
        ReqProfileDTO req = new ReqProfileDTO();
        req.setCvs(List.of(cv("1-u8-theirs.pdf", "x")));

        assertThrows(IdInvalidException.class, () -> service.saveMine(req));
        verify(profiles, never()).save(any());
    }

    @Test
    void aFileAlreadyOnTheProfileKeepsItsDateAndTheSameFileCountsOnce() throws Exception {
        Instant first = Instant.parse("2026-10-01T00:00:00Z");
        CandidateProfile.Cv existing = cv("1-u7-en.pdf", "old");
        existing.setUpdatedAt(first);
        profile.getCvs().add(existing);
        ReqProfileDTO req = new ReqProfileDTO();
        req.setCvs(List.of(cv("1-u7-en.pdf", "CV tiếng Anh"), cv("1-u7-en.pdf", "again")));

        service.saveMine(req);

        ArgumentCaptor<CandidateProfile> saved = ArgumentCaptor.forClass(CandidateProfile.class);
        verify(profiles).save(saved.capture());
        assertEquals(1, saved.getValue().getCvs().size());
        assertEquals(first, saved.getValue().getCvs().get(0).getUpdatedAt());
        assertEquals("CV tiếng Anh", saved.getValue().getCvs().get(0).getName());
    }

    @Test
    void anEmptyListRemovesEveryFile() throws Exception {
        profile.getCvs().add(cv("1-u7-en.pdf", "CV"));
        ReqProfileDTO req = new ReqProfileDTO();
        req.setCvs(List.of());

        assertTrue(service.saveMine(req).getCvs().isEmpty());
    }

    @Test
    void aFourthFileFailsValidation() {
        ReqProfileDTO req = new ReqProfileDTO();
        req.setCvs(List.of(cv("1-u7-a.pdf", "a"), cv("2-u7-b.pdf", "b"), cv("3-u7-c.pdf", "c"), cv("4-u7-d.pdf", "d")));

        var violations = Validation.buildDefaultValidatorFactory().getValidator().validate(req);

        assertEquals(List.of("Tối đa 3 tệp CV"), violations.stream().map(v -> v.getMessage()).toList());
    }
}
