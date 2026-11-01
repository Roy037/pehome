package vn.hoidanit.jobhunter.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.hoidanit.jobhunter.domain.Job;
import vn.hoidanit.jobhunter.domain.Subscriber;
import vn.hoidanit.jobhunter.repository.JobRepository;
import vn.hoidanit.jobhunter.repository.SubscriberRepository;

/**
 * Weekly "new jobs for your skills" e-mail. Each subscriber gets at most {@link #MAX_JOBS} postings that match one of
 * their skills, are still open and were published in the last {@link #WINDOW}; nothing is sent when there are none.
 */
@Service
public class JobAlertService {

    private static final Logger log = LoggerFactory.getLogger(JobAlertService.class);
    static final int MAX_JOBS = 10;
    static final Duration WINDOW = Duration.ofDays(7);

    private final SubscriberRepository subscriberRepository;
    private final JobRepository jobRepository;
    private final EmailService emailService;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    // Where mail apps' one-click unsubscribe button posts to: must be reachable from the internet in production.
    @Value("${app.backend-url:http://localhost:8080}")
    private String backendUrl;

    private final UnsubscribeTokens unsubscribeTokens;

    public JobAlertService(SubscriberRepository subscriberRepository, JobRepository jobRepository, EmailService emailService,
            UnsubscribeTokens unsubscribeTokens) {
        this.unsubscribeTokens = unsubscribeTokens;
        this.subscriberRepository = subscriberRepository;
        this.jobRepository = jobRepository;
        this.emailService = emailService;
    }

    // Mondays 08:00 Vietnam time by default; set app.job-alert.cron=- (JOB_ALERT_CRON=-) to switch the schedule off.
    @Scheduled(cron = "${app.job-alert.cron:0 0 8 * * MON}", zone = "Asia/Ho_Chi_Minh")
    public void sendWeekly() {
        int sent = sendDigest();
        log.info("Job alert digest: {} e-mail(s) dispatched", sent);
    }

    /** @return how many subscribers were e-mailed */
    @Transactional(readOnly = true)
    public int sendDigest() {
        Instant now = Instant.now();
        Instant since = now.minus(WINDOW);
        int sent = 0;
        for (Subscriber subscriber : this.subscriberRepository.findAllWithSkills()) {
            if (subscriber.getSkills() == null || subscriber.getSkills().isEmpty()) {
                continue;
            }
            List<Job> jobs = this.jobRepository.findAlertJobs(subscriber.getSkills(), now, since, PageRequest.of(0, MAX_JOBS));
            if (jobs.isEmpty()) {
                continue;
            }
            List<Map<String, Object>> rows = this.emailService.jobCards(jobs);
            String token = this.unsubscribeTokens.create(subscriber.getId());
            this.emailService.sendTemplate(subscriber.getEmail(), "Việc làm mới phù hợp với kỹ năng của bạn", "job-alert", Map.of(
                    "name", subscriber.getName() == null ? "bạn" : subscriber.getName(),
                    "jobs", rows,
                    "moreUrl", this.frontendUrl + "/job",
                    "unsubscribeUrl", this.frontendUrl + "/huy-nhan-tin?token=" + token,
                    "unsubscribeApiUrl", this.backendUrl + "/api/v1/subscribers/unsubscribe?token=" + token));
            sent++;
        }
        return sent;
    }
}
