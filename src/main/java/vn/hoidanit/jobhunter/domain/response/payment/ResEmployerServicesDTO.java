package vn.hoidanit.jobhunter.domain.response.payment;

import java.time.Instant;
import java.util.List;

/** What a company has and can buy: places for open jobs, pinned jobs, the talent unlock, and the products on sale. */
public record ResEmployerServicesDTO(int openJobs, int freeJobs, int jobLimit, Instant talentUntil, List<Pin> pins,
        List<Product> products, List<ResOrderDTO> orders) {

    public record Pin(long jobId, String jobName, Instant until) {
    }

    public record Product(String code, String label, long priceVnd, int days, int slots, boolean pin) {
    }
}
