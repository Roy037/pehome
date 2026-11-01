package vn.hoidanit.jobhunter.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import vn.hoidanit.jobhunter.domain.Job;
import vn.hoidanit.jobhunter.domain.Skill;
import vn.hoidanit.jobhunter.domain.Subscriber;
import vn.hoidanit.jobhunter.domain.response.ResultPaginationDTO;
import vn.hoidanit.jobhunter.domain.response.email.ResEmailJob;
import vn.hoidanit.jobhunter.repository.JobRepository;
import vn.hoidanit.jobhunter.repository.SkillRepository;
import vn.hoidanit.jobhunter.repository.SubscriberRepository;

@Service
public class SubscriberService {

    private final SubscriberRepository subscriberRepository;
    private final SkillRepository skillRepository;
    private final JobRepository jobRepository;
    private final NotificationService notificationService;
    private final UnsubscribeTokens unsubscribeTokens;

    public SubscriberService(
            SubscriberRepository subscriberRepository,
            SkillRepository skillRepository,
            JobRepository jobRepository,
            NotificationService notificationService,
            UnsubscribeTokens unsubscribeTokens) {
        this.unsubscribeTokens = unsubscribeTokens;
        this.subscriberRepository = subscriberRepository;
        this.skillRepository = skillRepository;
        this.jobRepository = jobRepository;
        this.notificationService = notificationService;

    }

    public boolean isExistsByEmail(String email) {
        return this.subscriberRepository.existsByEmail(email);
    }

    public Subscriber create(Subscriber subs) {
        subs.setId(0); // a create never replaces an existing row, whatever id the request body carries
        // check skills
        if (subs.getSkills() != null) {
            List<Long> reqSkills = subs.getSkills()
                    .stream().map(x -> x.getId())
                    .collect(Collectors.toList());

            List<Skill> dbSkills = this.skillRepository.findByIdIn(reqSkills);
            subs.setSkills(dbSkills);
        }

        subs = this.subscriberRepository.save(subs);
        this.notificationService.newsletterJoined(subs);
        return subs;
    }

    public Subscriber update(Subscriber subsDB, Subscriber subsRequest) {
        // check skills
        if (subsRequest.getSkills() != null) {
            List<Long> reqSkills = subsRequest.getSkills()
                    .stream().map(x -> x.getId())
                    .collect(Collectors.toList());

            List<Skill> dbSkills = this.skillRepository.findByIdIn(reqSkills);
            subsDB.setSkills(dbSkills);
        }
        return this.subscriberRepository.save(subsDB);
    }

    public ResultPaginationDTO fetchAll(Specification<Subscriber> spec, Pageable pageable) {
        Page<Subscriber> page = this.subscriberRepository.findAll(spec, pageable);
        ResultPaginationDTO rs = new ResultPaginationDTO();
        ResultPaginationDTO.Meta meta = new ResultPaginationDTO.Meta();
        meta.setPage(pageable.getPageNumber() + 1);
        meta.setPageSize(pageable.getPageSize());
        meta.setPages(page.getTotalPages());
        meta.setTotal(page.getTotalElements());
        rs.setMeta(meta);
        rs.setResult(page.getContent());
        return rs;
    }

    public Subscriber updateById(Subscriber subsDB, Subscriber req) {
        subsDB.setName(req.getName());
        subsDB.setEmail(req.getEmail());
        return update(subsDB, req);
    }

    public void delete(long id) {
        this.subscriberRepository.deleteById(id);
    }

    /** Signed link from an e-mail: removes the subscription. Already gone is fine (the link may be clicked twice). */
    public void unsubscribe(String token) throws vn.hoidanit.jobhunter.util.error.IdInvalidException {
        long id = this.unsubscribeTokens.parse(token)
                .orElseThrow(() -> new vn.hoidanit.jobhunter.util.error.IdInvalidException("Liên kết hủy đăng ký không hợp lệ."));
        if (this.subscriberRepository.existsById(id)) {
            this.subscriberRepository.deleteById(id);
        }
    }

    public Subscriber findById(long id) {
        Optional<Subscriber> subsOptional = this.subscriberRepository.findById(id);
        if (subsOptional.isPresent())
            return subsOptional.get();
        return null;
    }

    // public ResEmailJob convertJobToSendEmail(Job job) {
    // ResEmailJob res = new ResEmailJob();
    // res.setName(job.getName());
    // res.setSalary(job.getSalary());
    // res.setCompany(new ResEmailJob.CompanyEmail(job.getCompany().getName()));
    // List<Skill> skills = job.getSkills();
    // List<ResEmailJob.SkillEmail> s = skills.stream().map(skill -> new
    // ResEmailJob.SkillEmail(skill.getName()))
    // .collect(Collectors.toList());
    // res.setSkills(s);
    // return res;
    // }

    // public void sendSubscribersEmailJobs() {
    // List<Subscriber> listSubs = this.subscriberRepository.findAll();
    // if (listSubs != null && listSubs.size() > 0) {
    // for (Subscriber sub : listSubs) {
    // List<Skill> listSkills = sub.getSkills();
    // if (listSkills != null && listSkills.size() > 0) {
    // List<Job> listJobs = this.jobRepository.findBySkillsIn(listSkills);
    // if (listJobs != null && listJobs.size() > 0) {

    // List<ResEmailJob> arr = listJobs.stream().map(
    // job -> this.convertJobToSendEmail(job)).collect(Collectors.toList());

    // this.emailService.sendEmailFromTemplateSync(
    // sub.getEmail(),
    // "Cơ hội việc làm hot đang chờ đón bạn, khám phá ngay",
    // "job",
    // sub.getName(),
    // arr);
    // }
    // }

    public Subscriber findByEmail(String email) {
        return this.subscriberRepository.findByEmail(email);
    }
}