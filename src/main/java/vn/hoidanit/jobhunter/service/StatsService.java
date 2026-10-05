package vn.hoidanit.jobhunter.service;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import vn.hoidanit.jobhunter.domain.response.ResAdminStatsDTO;
import vn.hoidanit.jobhunter.util.constant.PlanEnum;

@Service
public class StatsService {
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int MONTHS = 6;

    private final EntityManager em;

    public StatsService(EntityManager em) {
        this.em = em;
    }

    private long count(String jpql) {
        return this.em.createQuery(jpql, Long.class).getSingleResult();
    }

    private long count(String jpql, Instant now) {
        return this.em.createQuery(jpql, Long.class).setParameter("now", now).getSingleResult();
    }

    @Transactional(readOnly = true)
    public ResAdminStatsDTO overview() {
        Instant now = Instant.now();

        long companies = count("select count(c) from Company c");
        long approved = count("select count(c) from Company c where c.approved = true");
        long rejected = count("select count(c) from Company c where c.approved = false and c.rejectionReason is not null");

        List<ResAdminStatsDTO.PlanSales> byPlan = new ArrayList<>();
        long total = 0, orders = 0;
        for (PlanEnum plan : PlanEnum.values()) {
            Object[] row = this.em.createQuery("select count(o), coalesce(sum(o.amount), 0) from PlanOrder o "
                    + "where o.status = 'PAID' and o.plan = :plan", Object[].class).setParameter("plan", plan).getSingleResult();
            byPlan.add(new ResAdminStatsDTO.PlanSales(plan.name(), plan.getLabel(), (Long) row[0], ((Number) row[1]).longValue()));
            orders += (Long) row[0];
            total += ((Number) row[1]).longValue();
        }
        long last30 = this.em.createQuery("select coalesce(sum(o.amount), 0) from PlanOrder o where o.status = 'PAID' and o.paidAt >= :since", Long.class)
                .setParameter("since", now.minus(30, ChronoUnit.DAYS)).getSingleResult();

        return new ResAdminStatsDTO(
                new ResAdminStatsDTO.Users(count("select count(u) from User u"),
                        count("select count(u) from User u where u.role.name = 'NORMAL_USER'"),
                        count("select count(u) from User u where u.company is not null"),
                        count("select count(distinct o.user.id) from PlanOrder o where o.status = 'PAID' and o.startsAt <= :now and o.endsAt > :now", now)),
                new ResAdminStatsDTO.Companies(companies, approved, companies - approved - rejected, rejected),
                new ResAdminStatsDTO.Jobs(count("select count(j) from Job j"),
                        count("select count(j) from Job j where j.active = true and j.locked = false "
                                + "and (j.endDate is null or j.endDate > :now) and (j.startDate is null or j.startDate <= :now)", now),
                        count("select count(j) from Job j where j.locked = true"),
                        count("select count(r) from JobReport r")),
                new ResAdminStatsDTO.Applications(count("select count(r) from Resume r"),
                        count("select count(r) from Resume r where r.status = 'PENDING'")),
                new ResAdminStatsDTO.Revenue(total, last30, orders,
                        count("select count(distinct o.user.id) from PlanOrder o where o.status = 'PAID'"), byPlan),
                months());
    }

    // ponytail: buckets the timestamps in Java so month edges follow Vietnam time; move to SQL GROUP BY if these tables get huge
    private List<ResAdminStatsDTO.Month> months() {
        YearMonth first = YearMonth.now(VN).minusMonths(MONTHS - 1);
        Instant since = first.atDay(1).atStartOfDay(VN).toInstant();
        long[] signups = bucket("select u.createdAt from User u where u.createdAt >= :since", since, first);
        long[] applications = bucket("select r.createdAt from Resume r where r.createdAt >= :since", since, first);
        long[] revenue = new long[MONTHS];
        for (Object[] row : this.em.createQuery("select o.paidAt, o.amount from PlanOrder o where o.status = 'PAID' and o.paidAt >= :since", Object[].class)
                .setParameter("since", since).getResultList()) {
            int i = slot((Instant) row[0], first);
            if (i >= 0 && i < MONTHS) revenue[i] += ((Number) row[1]).longValue();
        }
        List<ResAdminStatsDTO.Month> months = new ArrayList<>();
        for (int i = 0; i < MONTHS; i++) {
            months.add(new ResAdminStatsDTO.Month(first.plusMonths(i).toString(), signups[i], applications[i], revenue[i]));
        }
        return months;
    }

    private long[] bucket(String jpql, Instant since, YearMonth first) {
        long[] counts = new long[MONTHS];
        for (Instant at : this.em.createQuery(jpql, Instant.class).setParameter("since", since).getResultList()) {
            int i = slot(at, first);
            if (i >= 0 && i < MONTHS) counts[i]++;
        }
        return counts;
    }

    private int slot(Instant at, YearMonth first) {
        return (int) ChronoUnit.MONTHS.between(first, YearMonth.from(at.atZone(VN)));
    }
}
