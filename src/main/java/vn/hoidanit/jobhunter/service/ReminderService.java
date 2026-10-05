package vn.hoidanit.jobhunter.service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.hoidanit.jobhunter.domain.Company;
import vn.hoidanit.jobhunter.domain.Job;
import vn.hoidanit.jobhunter.domain.Resume;
import vn.hoidanit.jobhunter.domain.SavedJob;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.repository.JobRepository;
import vn.hoidanit.jobhunter.repository.ResumeRepository;
import vn.hoidanit.jobhunter.repository.SavedJobRepository;

/**
 * The time-based e-mails. Each run looks at a window of exactly one run's length, so a post, interview or application
 * lands in one window only and nothing is stored to remember what was sent. The cost: a run missed because the app was
 * down is not made up. Called through {@link ReminderScheduler} (a separate bean, so the transaction below applies).
 */
@Service
public class ReminderService {

    /** Posts and saved jobs closing 24 to 48 hours from now: "within two days". */
    static final Duration CLOSING_FROM = Duration.ofHours(24);
    static final Duration CLOSING_TO = Duration.ofHours(48);
    /** Interviews that start 23 to 24 hours from the top of the hour: the day-before reminder. */
    static final Duration INTERVIEW_FROM = Duration.ofHours(23);
    static final Duration INTERVIEW_TO = Duration.ofHours(24);

    private final JobRepository jobRepository;
    private final SavedJobRepository savedJobRepository;
    private final ResumeRepository resumeRepository;
    private final NotificationService notifications;

    public ReminderService(JobRepository jobRepository, SavedJobRepository savedJobRepository,
            ResumeRepository resumeRepository, NotificationService notifications) {
        this.jobRepository = jobRepository;
        this.savedJobRepository = savedJobRepository;
        this.resumeRepository = resumeRepository;
        this.notifications = notifications;
    }

    /** Candidates: saved posts closing soon (one e-mail per candidate). @return e-mails dispatched */
    @Transactional(readOnly = true)
    public int savedJobsClosing(Instant now) {
        Map<User, List<Job>> perUser = new LinkedHashMap<>();
        for (SavedJob saved : this.savedJobRepository.findClosingBetween(now.plus(CLOSING_FROM), now.plus(CLOSING_TO))) {
            perUser.computeIfAbsent(saved.getUser(), user -> new ArrayList<>()).add(saved.getJob());
        }
        perUser.forEach(this.notifications::savedJobsClosing);
        return perUser.size();
    }

    /** Employers: posts closing soon (one e-mail per employer account, listing that company's posts). @return companies notified */
    @Transactional(readOnly = true)
    public int jobsExpiring(Instant now) {
        Map<Company, List<Job>> perCompany = new LinkedHashMap<>();
        for (Job job : this.jobRepository.findEndingBetween(now.plus(CLOSING_FROM), now.plus(CLOSING_TO))) {
            perCompany.computeIfAbsent(job.getCompany(), company -> new ArrayList<>()).add(job);
        }
        perCompany.forEach(this.notifications::jobsExpiring);
        return perCompany.size();
    }

    /** Candidate and employers: interviews about 24 hours away. Run at the top of every hour. @return interviews reminded */
    @Transactional(readOnly = true)
    public int interviewsTomorrow(Instant now) {
        Instant hour = now.truncatedTo(ChronoUnit.HOURS);
        List<Resume> due = this.resumeRepository.findInterviewsBetween(hour.plus(INTERVIEW_FROM), hour.plus(INTERVIEW_TO));
        due.forEach(this.notifications::interviewTomorrow);
        return due.size();
    }

    /** Employers: the applications of the last 24 hours, one e-mail per company account. @return companies notified */
    @Transactional(readOnly = true)
    public int hrDailyDigest(Instant now) {
        Map<Company, List<Resume>> perCompany = new LinkedHashMap<>();
        for (Resume resume : this.resumeRepository.findReceivedBetween(now.minus(Duration.ofHours(24)), now)) {
            perCompany.computeIfAbsent(resume.getJob().getCompany(), company -> new ArrayList<>()).add(resume);
        }
        perCompany.forEach(this.notifications::hrDailyDigest);
        return perCompany.size();
    }
}
