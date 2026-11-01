package vn.hoidanit.jobhunter.service;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import vn.hoidanit.jobhunter.repository.JobRepository;

/** A pinned job sorts first only while its pin runs; this clears the ones that have run out. */
@Component
public class PinScheduler {
    private static final Logger log = LoggerFactory.getLogger(PinScheduler.class);

    private final JobRepository jobRepository;

    public PinScheduler(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    // Every 10 minutes; set app.pin.cleanup-cron=- (PIN_CLEANUP_CRON=-) to switch it off.
    @Scheduled(cron = "${app.pin.cleanup-cron:0 */10 * * * *}", zone = "Asia/Ho_Chi_Minh")
    public void clearExpiredPins() {
        int cleared = this.jobRepository.clearExpiredPins(Instant.now());
        if (cleared > 0) {
            log.info("Unpinned {} job(s) whose pin ran out", cleared);
        }
    }
}
