package vn.hoidanit.jobhunter.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import vn.hoidanit.jobhunter.domain.PlanOrder;
import vn.hoidanit.jobhunter.util.constant.PlanEnum;
import vn.hoidanit.jobhunter.util.constant.OrderStatusEnum;
import vn.hoidanit.jobhunter.util.constant.PaymentMethodEnum;

@Repository
public interface PlanOrderRepository extends JpaRepository<PlanOrder, Long>, JpaSpecificationExecutor<PlanOrder> {
    List<PlanOrder> findTop50ByUserIdOrderByCreatedAtDesc(long userId);

    boolean existsByUserId(long userId);

    // the row is locked so a return redirect and the gateway's IPN arriving together cannot both confirm it
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PlanOrder o where o.txnRef = :txnRef")
    Optional<PlanOrder> findByTxnRefForUpdate(@Param("txnRef") String txnRef);

    Optional<PlanOrder> findByTxnRef(String txnRef);

    List<PlanOrder> findTop20ByMethodAndStatusAndCreatedAtBetweenOrderByCreatedAtAsc(
            PaymentMethodEnum method, OrderStatusEnum status, Instant from, Instant to);

    @Query("select o from PlanOrder o where o.user.id = :userId and o.status = 'PAID' and o.startsAt <= :now and o.endsAt > :now")
    List<PlanOrder> findActive(@Param("userId") long userId, @Param("now") Instant now);

    @Query("select o from PlanOrder o where o.user.id in :userIds and o.status = 'PAID' and o.startsAt <= :now and o.endsAt > :now")
    List<PlanOrder> findActiveForUsers(@Param("userIds") Collection<Long> userIds, @Param("now") Instant now);

    // when the last paid pass of this plan ends, so a repeat purchase continues from there instead of overlapping
    @Query("select max(o.endsAt) from PlanOrder o where o.user.id = :userId and o.plan = :plan and o.status = 'PAID'")
    Instant latestEnd(@Param("userId") long userId, @Param("plan") PlanEnum plan);

    // the user is fetched along, the reminder runs outside a request
    @Query("select o from PlanOrder o join fetch o.user where o.status = 'PAID' and o.endsAt >= :from and o.endsAt < :to")
    List<PlanOrder> findPaidEndingBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query("select max(o.endsAt) from PlanOrder o where o.user.id = :userId and o.status = 'PAID'")
    Instant latestEndAnyPlan(@Param("userId") long userId);
}
