package vn.hoidanit.jobhunter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import vn.hoidanit.jobhunter.domain.CandidateProfile;
import vn.hoidanit.jobhunter.domain.Company;
import vn.hoidanit.jobhunter.domain.Job;
import vn.hoidanit.jobhunter.domain.PlanOrder;
import vn.hoidanit.jobhunter.domain.Role;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.domain.response.payment.ResCreateOrderDTO;
import vn.hoidanit.jobhunter.repository.CandidateProfileRepository;
import vn.hoidanit.jobhunter.repository.CompanyRepository;
import vn.hoidanit.jobhunter.repository.JobRepository;
import vn.hoidanit.jobhunter.repository.PlanOrderRepository;
import vn.hoidanit.jobhunter.repository.ResumeRepository;
import vn.hoidanit.jobhunter.repository.SavedJobRepository;
import vn.hoidanit.jobhunter.repository.SkillRepository;
import vn.hoidanit.jobhunter.repository.SubscriberRepository;
import vn.hoidanit.jobhunter.util.constant.EmployerProductEnum;
import vn.hoidanit.jobhunter.util.constant.OrderStatusEnum;
import vn.hoidanit.jobhunter.util.constant.PaymentMethodEnum;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;
import vn.hoidanit.jobhunter.util.error.PermissionException;

/** What an employer buys: a pinned job, places for open jobs, the talent directory. */
class EmployerProductsTests {
    private final PlanOrderRepository orders = mock(PlanOrderRepository.class);
    private final JobRepository jobs = mock(JobRepository.class);
    private final VnpayService vnpay = mock(VnpayService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private PaymentService payments;
    private User hr;
    private Company company;

    @BeforeEach
    void setup() {
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(this.vnpay.isMock()).thenReturn(true);
        when(this.orders.save(any(PlanOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        this.payments = new PaymentService(this.orders, mock(SubscriberRepository.class), mock(SavedJobRepository.class),
                mock(PlanService.class), this.vnpay, this.notifications, mock(ZalopayService.class), mock(MomoService.class),
                transactions, this.jobs);
        this.company = new Company();
        this.company.setId(5);
        this.company.setApproved(true);
        this.hr = new User();
        this.hr.setId(9);
        this.hr.setCompany(this.company);
        this.hr.setTermsVersion(User.TERMS_VERSION);
    }

    private Job job(long id, long companyId, boolean active) {
        Company owner = new Company();
        owner.setId(companyId);
        Job job = new Job();
        job.setId(id);
        job.setCompany(owner);
        job.setActive(active);
        when(this.jobs.findById(id)).thenReturn(Optional.of(job));
        return job;
    }

    private PlanOrder paid(EmployerProductEnum product, Long jobId) {
        PlanOrder order = new PlanOrder();
        order.setUser(this.hr);
        order.setProduct(product);
        order.setCompanyId(5L);
        order.setJobId(jobId);
        order.setTxnRef("T" + product.name());
        order.setMethod(PaymentMethodEnum.VNPAY);
        order.setAmount(product.getPriceVnd());
        order.setStatus(OrderStatusEnum.PENDING);
        return order;
    }

    // paying goes through the gateway answer; the mock gateway signs it like VNPay does
    private void pay(PlanOrder order) {
        when(this.orders.findByTxnRefForUpdate(order.getTxnRef())).thenReturn(Optional.of(order));
        when(this.vnpay.verify(any())).thenReturn(true);
        when(this.vnpay.isMock()).thenReturn(true);
        this.payments.confirm(java.util.Map.of("vnp_TxnRef", order.getTxnRef(), "vnp_Amount", String.valueOf(order.getAmount() * 100),
                "vnp_ResponseCode", "00", "vnp_TransactionStatus", "00"));
    }

    @Test
    void aPinRunsFromNowAndSetsThePinOnTheJob() {
        when(this.jobs.pinnedUntilOf(40L)).thenReturn(null);
        PlanOrder order = paid(EmployerProductEnum.JOB_PIN_7, 40L);
        pay(order);
        assertEquals(OrderStatusEnum.PAID, order.getStatus());
        assertEquals(Duration.ofDays(7), Duration.between(order.getStartsAt(), order.getEndsAt()));
        verify(this.jobs).pin(40L, order.getEndsAt());
        verify(this.orders, never()).latestEnd(anyLong(), any());
        verify(this.notifications).paymentReceipt(order);
    }

    @Test
    void aSecondPinOnTheSameJobContinuesWhereTheFirstEnds() {
        Instant running = Instant.now().plus(Duration.ofDays(3));
        when(this.jobs.pinnedUntilOf(40L)).thenReturn(running);
        PlanOrder order = paid(EmployerProductEnum.JOB_PIN_30, 40L);
        pay(order);
        assertEquals(running, order.getStartsAt());
        assertEquals(running.plus(Duration.ofDays(30)), order.getEndsAt());
        verify(this.jobs).pin(40L, order.getEndsAt());
    }

    @Test
    void jobPacksRunSideBySideAndTheTalentUnlockContinues() {
        Instant later = Instant.now().plus(Duration.ofDays(10));
        when(this.orders.latestCompanyEnd(5L, EmployerProductEnum.JOB_SLOTS_5)).thenReturn(later);
        PlanOrder pack = paid(EmployerProductEnum.JOB_SLOTS_5, null);
        pay(pack);
        assertTrue(Duration.between(pack.getStartsAt(), Instant.now()).abs().getSeconds() < 5, "starts now, not after the other pack");
        verify(this.jobs, never()).pin(anyLong(), any());

        when(this.orders.latestCompanyEnd(5L, EmployerProductEnum.TALENT_30)).thenReturn(later);
        PlanOrder talent = paid(EmployerProductEnum.TALENT_30, null);
        pay(talent);
        assertEquals(later, talent.getStartsAt());
    }

    @Test
    void onlyAnApprovedEmployerWhoAcceptedTheTermsBuysAndOnlyForItsOwnOpenJob() throws Exception {
        job(40, 5, true);
        job(41, 6, true);
        job(42, 5, false);

        ResCreateOrderDTO created = this.payments.createEmployerOrder(this.hr, EmployerProductEnum.JOB_PIN_7, 40L,
                PaymentMethodEnum.VNPAY, "1.2.3.4");
        assertTrue(created.mock());
        ArgumentCaptor<PlanOrder> saved = ArgumentCaptor.forClass(PlanOrder.class);
        verify(this.orders).save(saved.capture());
        assertEquals(EmployerProductEnum.JOB_PIN_7, saved.getValue().getProduct());
        assertEquals(99_000, saved.getValue().getAmount());
        assertEquals(5L, saved.getValue().getCompanyId());
        assertNull(saved.getValue().getPlan());

        assertThrows(PermissionException.class, () -> this.payments.createEmployerOrder(this.hr, EmployerProductEnum.JOB_PIN_7,
                41L, null, "1.2.3.4"), "another company's job");
        assertThrows(IdInvalidException.class, () -> this.payments.createEmployerOrder(this.hr, EmployerProductEnum.JOB_PIN_7,
                42L, null, "1.2.3.4"), "a job that is switched off");
        assertThrows(PermissionException.class, () -> this.payments.createEmployerOrder(this.hr, EmployerProductEnum.JOB_PIN_7,
                null, null, "1.2.3.4"), "a pin without a job");

        this.company.setApproved(false);
        assertThrows(PermissionException.class, () -> this.payments.createEmployerOrder(this.hr, EmployerProductEnum.TALENT_30,
                null, null, "1.2.3.4"), "company not approved");
        this.company.setApproved(true);
        this.hr.setTermsVersion(null);
        assertThrows(PermissionException.class, () -> this.payments.createEmployerOrder(this.hr, EmployerProductEnum.TALENT_30,
                null, null, "1.2.3.4"), "terms not accepted");
        this.hr.setCompany(null);
        assertThrows(PermissionException.class, () -> this.payments.createEmployerOrder(this.hr, EmployerProductEnum.TALENT_30,
                null, null, "1.2.3.4"), "a candidate");
    }

    @Test
    void theGatewaysPrintAnAsciiNameAndEmployersComeBackToTheirOwnArea() {
        PlanOrder pin = paid(EmployerProductEnum.JOB_PIN_7, 1L);
        assertEquals("ghim tin 7 ngay", pin.itemName());
        assertEquals("Ghim tin 7 ngày", pin.itemLabel());
        assertEquals("/admin/dich-vu", pin.returnPath());
        PlanOrder plan = new PlanOrder();
        plan.setPlan(vn.hoidanit.jobhunter.util.constant.PlanEnum.STANDARD);
        assertEquals("goi Standard", plan.itemName());
        assertEquals("Gói Standard", plan.itemLabel());
        assertEquals("/", plan.returnPath());
    }

    @Test
    void jobLimitAddsTheRunningPacksToTheFreePlaces() {
        PlanOrderRepository repository = mock(PlanOrderRepository.class);
        PlanOrder pack = paid(EmployerProductEnum.JOB_SLOTS_5, null);
        PlanOrder unlock = paid(EmployerProductEnum.TALENT_30, null);
        unlock.setEndsAt(Instant.now().plus(Duration.ofDays(5)));
        when(repository.findActiveForCompany(anyLong(), any())).thenReturn(List.of(pack, pack, unlock));
        PlanService plans = new PlanService(repository);
        assertEquals(EmployerProductEnum.FREE_OPEN_JOBS + 10, plans.employerJobLimit(5, Instant.now()));
        assertEquals(unlock.getEndsAt(), plans.talentUnlockedUntil(5, Instant.now()));
    }

    @Test
    void aCompanyCannotOpenMoreJobsThanItsPlacesAndCannotPinThroughTheBody() throws Exception {
        JobRepository jobRepository = mock(JobRepository.class);
        UserService users = mock(UserService.class);
        PlanService plans = mock(PlanService.class);
        JobService service = new JobService(jobRepository, mock(SkillRepository.class), mock(CompanyRepository.class), users,
                mock(ResumeRepository.class), mock(NotificationService.class), plans);
        when(users.currentUserCompany()).thenReturn(this.company);
        when(users.currentUserOrNull()).thenReturn(this.hr);
        when(plans.employerJobLimit(5, Instant.now())).thenReturn(3);
        when(plans.employerJobLimit(anyLong(), any())).thenReturn(3);
        Job body = new Job();
        body.setName("QA");
        body.setSalary(1_000_000);
        body.setActive(true);
        body.setPinnedUntil(Instant.now().plus(Duration.ofDays(365)));

        when(jobRepository.countOpen(anyLong(), anyLong(), any())).thenReturn(3L);
        assertThrows(PermissionException.class, () -> service.create(body));
        verify(jobRepository, never()).save(any(Job.class));

        when(jobRepository.countOpen(anyLong(), anyLong(), any())).thenReturn(2L);
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));
        service.create(body);
        ArgumentCaptor<Job> saved = ArgumentCaptor.forClass(Job.class);
        verify(jobRepository).save(saved.capture());
        assertNull(saved.getValue().getPinnedUntil(), "only a paid order pins a job");
    }

    @Test
    void theTalentListIsFreeButProfilesAndCvsNeedTheUnlock() throws Exception {
        CandidateProfileRepository profiles = mock(CandidateProfileRepository.class);
        UserService users = mock(UserService.class);
        PlanService plans = mock(PlanService.class);
        TalentService talent = new TalentService(profiles, users, plans);
        CandidateProfile profile = new CandidateProfile();
        profile.setCvUrl("cv.pdf");
        when(profiles.findOne(any(Specification.class))).thenReturn(Optional.of(profile));
        when(users.handleGetCurrentUser()).thenReturn(this.hr);

        when(plans.talentUnlockedUntil(anyLong(), any())).thenReturn(null);
        assertThrows(PermissionException.class, () -> talent.cvOf(1));

        when(plans.talentUnlockedUntil(anyLong(), any())).thenReturn(Instant.now().plus(Duration.ofDays(2)));
        assertEquals("cv.pdf", talent.cvOf(1));

        User admin = new User();
        Role role = new Role();
        role.setName("SUPER_ADMIN");
        admin.setRole(role);
        when(users.handleGetCurrentUser()).thenReturn(admin);
        when(plans.talentUnlockedUntil(anyLong(), any())).thenReturn(null);
        assertEquals("cv.pdf", talent.cvOf(1), "an admin never needs the unlock");
    }
}
