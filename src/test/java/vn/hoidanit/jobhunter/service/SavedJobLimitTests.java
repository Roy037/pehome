package vn.hoidanit.jobhunter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import vn.hoidanit.jobhunter.domain.Job;
import vn.hoidanit.jobhunter.domain.SavedJob;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.repository.JobRepository;
import vn.hoidanit.jobhunter.repository.SavedJobRepository;
import vn.hoidanit.jobhunter.util.constant.PlanEnum;
import vn.hoidanit.jobhunter.util.error.PermissionException;

class SavedJobLimitTests {
    SavedJobRepository saved = mock(SavedJobRepository.class);
    JobRepository jobs = mock(JobRepository.class);
    UserService users = mock(UserService.class);
    PlanService plans = mock(PlanService.class);
    SavedJobService service = new SavedJobService(saved, jobs, users, plans);
    Job job = new Job();

    @BeforeEach
    void setUp() throws Exception {
        User candidate = new User();
        candidate.setId(7);
        job.setId(40);
        when(users.currentCandidate()).thenReturn(candidate);
        when(users.handleGetCurrentUser()).thenReturn(candidate);
        when(jobs.findById(40L)).thenReturn(Optional.of(job));
        when(plans.savedJobCap(7L)).thenReturn(PlanEnum.FREE_SAVED_JOBS);
    }

    @Test
    void savingTheSameJobTwiceKeepsOneRow() throws Exception {
        when(saved.existsByUserIdAndJobId(7L, 40L)).thenReturn(true);

        service.save(40);

        verify(saved, never()).save(any());
    }

    @Test
    void theFreeTierStopsAtItsCapAndSaysWhichPlanToBuy() throws Exception {
        when(saved.countByUserId(7L)).thenReturn((long) PlanEnum.FREE_SAVED_JOBS - 1);
        service.save(40);
        verify(saved).save(any(SavedJob.class));

        when(saved.countByUserId(7L)).thenReturn((long) PlanEnum.FREE_SAVED_JOBS);
        PermissionException full = assertThrows(PermissionException.class, () -> service.save(40));
        assertTrue(full.getMessage().contains("Miễn phí"), full.getMessage());
        assertTrue(full.getMessage().contains(String.valueOf(PlanEnum.FREE_SAVED_JOBS)), full.getMessage());
    }

    @Test
    void aJobThatGetsLockedDropsOutOfMyList() throws Exception {
        Job locked = new Job();
        locked.setLocked(true);
        SavedJob a = new SavedJob();
        a.setJob(job);
        SavedJob b = new SavedJob();
        b.setJob(locked);
        when(saved.findByUserIdOrderByCreatedAtDesc(7L)).thenReturn(List.of(a, b));

        assertEquals(List.of(job), service.fetchMine());
    }
}
