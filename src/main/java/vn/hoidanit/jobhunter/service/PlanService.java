package vn.hoidanit.jobhunter.service;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import vn.hoidanit.jobhunter.domain.PlanOrder;
import vn.hoidanit.jobhunter.repository.PlanOrderRepository;
import vn.hoidanit.jobhunter.util.constant.EmployerProductEnum;
import vn.hoidanit.jobhunter.util.constant.PlanEnum;

/** Which premium plan a candidate has right now, and what it allows. No plan means the free tier. */
@Service
public class PlanService {
    private final PlanOrderRepository orderRepository;

    public PlanService(PlanOrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /** The best plan with a pass that is active at this moment, or null for the free tier. */
    public PlanEnum activePlan(long userId) {
        return best(this.orderRepository.findActive(userId, Instant.now()));
    }

    public Instant activeUntil(long userId, PlanEnum plan) {
        return this.orderRepository.findActive(userId, Instant.now()).stream()
                .filter(order -> order.getPlan() == plan).map(PlanOrder::getEndsAt).max(Comparator.naturalOrder()).orElse(null);
    }

    public Map<Long, PlanEnum> activePlans(Collection<Long> userIds) {
        Map<Long, PlanEnum> result = new HashMap<>();
        if (userIds.isEmpty()) {
            return result;
        }
        for (PlanOrder order : this.orderRepository.findActiveForUsers(userIds, Instant.now())) {
            result.merge(order.getUser().getId(), order.getPlan(), (a, b) -> a.ordinal() >= b.ordinal() ? a : b);
        }
        return result;
    }

    public int alertSkillCap(long userId) {
        PlanEnum plan = activePlan(userId);
        return plan == null ? PlanEnum.FREE_ALERT_SKILLS : plan.getAlertSkills();
    }

    public int savedJobCap(long userId) {
        PlanEnum plan = activePlan(userId);
        return plan == null ? PlanEnum.FREE_SAVED_JOBS : plan.getSavedJobs();
    }

    public static String label(PlanEnum plan) {
        return plan == null ? "Miễn phí" : plan.getLabel();
    }

    private static PlanEnum best(List<PlanOrder> active) {
        return active.stream().map(PlanOrder::getPlan).max(Comparator.naturalOrder()).orElse(null);
    }

    // ---- what a company has bought (employer products) ----

    /** How many jobs a company may have open at once: the free places plus every job pack that is running. */
    public int employerJobLimit(long companyId, Instant now) {
        return EmployerProductEnum.FREE_OPEN_JOBS + this.orderRepository.findActiveForCompany(companyId, now).stream()
                .mapToInt(order -> order.getProduct().getSlots()).sum();
    }

    /** Until when the company can open candidate profiles and CVs in the talent directory, or null. */
    public Instant talentUnlockedUntil(long companyId, Instant now) {
        return this.orderRepository.findActiveForCompany(companyId, now).stream()
                .filter(order -> order.getProduct() == EmployerProductEnum.TALENT_30).map(PlanOrder::getEndsAt)
                .max(Comparator.naturalOrder()).orElse(null);
    }
}
