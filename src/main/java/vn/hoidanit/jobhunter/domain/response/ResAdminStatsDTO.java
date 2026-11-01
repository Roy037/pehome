package vn.hoidanit.jobhunter.domain.response;

import java.util.List;

/** Platform-wide numbers for the admin dashboard. Money is VND. */
public record ResAdminStatsDTO(Users users, Companies companies, Jobs jobs, Applications applications, Revenue revenue,
        List<Month> months) {
    /** premium = users whose paid pass is active right now. */
    public record Users(long total, long candidates, long employers, long premium) {
    }

    public record Companies(long total, long approved, long pending, long rejected) {
    }

    public record Jobs(long total, long open, long locked, long reports) {
    }

    public record Applications(long total, long pending) {
    }

    public record Revenue(long total, long last30Days, long paidOrders, long payingUsers, List<PlanSales> byPlan) {
    }

    public record PlanSales(String plan, String label, long orders, long amount) {
    }

    /** month is "YYYY-MM" in Vietnam time; the list covers the last 6 months, oldest first. */
    public record Month(String month, long signups, long applications, long revenue) {
    }
}
