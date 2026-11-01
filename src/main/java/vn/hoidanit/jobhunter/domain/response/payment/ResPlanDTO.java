package vn.hoidanit.jobhunter.domain.response.payment;

public record ResPlanDTO(String code, String name, long priceVnd, int days, int alertSkills, int savedJobs, boolean highlight) {
}
