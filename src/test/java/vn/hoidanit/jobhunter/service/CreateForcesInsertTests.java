package vn.hoidanit.jobhunter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import vn.hoidanit.jobhunter.domain.Job;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.repository.CompanyRepository;
import vn.hoidanit.jobhunter.repository.JobRepository;
import vn.hoidanit.jobhunter.repository.PlanOrderRepository;
import vn.hoidanit.jobhunter.repository.ResumeRepository;
import vn.hoidanit.jobhunter.repository.SkillRepository;
import vn.hoidanit.jobhunter.repository.UserRepository;

class CreateForcesInsertTests {
    @Test
    void createUserIgnoresAnIdFromTheRequestBody() {
        UserRepository users = mock(UserRepository.class);
        UserService service = new UserService(users, mock(CompanyService.class), mock(RoleService.class),
                mock(ResumeRepository.class), mock(PlanOrderRepository.class), mock(NotificationService.class));
        User body = new User();
        body.setId(1);

        service.handleCreateUser(body);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertEquals(0, saved.getValue().getId());
    }

    @Test
    void createJobIgnoresAnIdFromTheRequestBody() throws Exception {
        JobRepository jobs = mock(JobRepository.class);
        when(jobs.save(any(Job.class))).thenAnswer(call -> call.getArgument(0));
        JobService service = new JobService(jobs, mock(SkillRepository.class), mock(CompanyRepository.class),
                mock(UserService.class), mock(ResumeRepository.class), mock(NotificationService.class), mock(PlanService.class));
        Job body = new Job();
        body.setId(1);

        service.create(body);

        ArgumentCaptor<Job> saved = ArgumentCaptor.forClass(Job.class);
        verify(jobs).save(saved.capture());
        assertEquals(0, saved.getValue().getId());
    }
}
