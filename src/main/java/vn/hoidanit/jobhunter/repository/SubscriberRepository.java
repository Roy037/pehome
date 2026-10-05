package vn.hoidanit.jobhunter.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import vn.hoidanit.jobhunter.domain.Subscriber;

@Repository
public interface SubscriberRepository extends JpaRepository<Subscriber, Long>,
        JpaSpecificationExecutor<Subscriber> {

    boolean existsByEmail(String email);

    Subscriber findByEmail(String email);

    // skills come along: the weekly digest runs on the scheduler thread, outside any transaction
    @Query("select distinct s from Subscriber s left join fetch s.skills")
    List<Subscriber> findAllWithSkills();
}