package vn.hoidanit.jobhunter.service;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Cron triggers for {@link ReminderService}. Each schedule can be switched off with "-" (see application.properties). */
@Component
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);
    private static final String VN = "Asia/Ho_Chi_Minh";

    private final ReminderService reminders;

    public ReminderScheduler(ReminderService reminders) {
        this.reminders = reminders;
    }

    @Scheduled(cron = "${app.hr-digest.cron:0 30 8 * * *}", zone = VN)
    public void hrDailyDigest() {
        log.info("Employer daily digest: {} company(ies) e-mailed", this.reminders.hrDailyDigest(Instant.now()));
    }

    @Scheduled(cron = "${app.saved-job-reminder.cron:0 30 9 * * *}", zone = VN)
    public void savedJobsClosing() {
        log.info("Saved-job deadline reminders: {} candidate(s) e-mailed", this.reminders.savedJobsClosing(Instant.now()));
    }

    @Scheduled(cron = "${app.job-expiry-reminder.cron:0 0 10 * * *}", zone = VN)
    public void jobsExpiring() {
        log.info("Job expiry reminders: {} company(ies) e-mailed", this.reminders.jobsExpiring(Instant.now()));
    }

    @Scheduled(cron = "${app.interview-reminder.cron:0 0 * * * *}", zone = VN)
    public void interviewsTomorrow() {
        int sent = this.reminders.interviewsTomorrow(Instant.now());
        if (sent > 0) {
            log.info("Interview reminders: {} interview(s) reminded", sent);
        }
    }
}
