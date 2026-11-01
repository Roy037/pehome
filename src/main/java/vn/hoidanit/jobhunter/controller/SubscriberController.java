package vn.hoidanit.jobhunter.controller;

import vn.hoidanit.jobhunter.util.error.ResourceNotFoundException;
import vn.hoidanit.jobhunter.util.error.ConflictException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.turkraft.springfilter.boot.Filter;

import jakarta.validation.Valid;
import vn.hoidanit.jobhunter.domain.Subscriber;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.domain.response.ResultPaginationDTO;
import vn.hoidanit.jobhunter.service.EmailVerificationService;
import vn.hoidanit.jobhunter.service.JobAlertService;
import vn.hoidanit.jobhunter.service.PlanService;
import vn.hoidanit.jobhunter.service.SubscriberService;
import vn.hoidanit.jobhunter.service.UserService;
import vn.hoidanit.jobhunter.util.SecurityUtil;
import vn.hoidanit.jobhunter.util.annotation.ApiMessage;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;
import vn.hoidanit.jobhunter.util.error.PermissionException;

@RestController
@RequestMapping("/api/v1")
public class SubscriberController {
    private final SubscriberService subscriberService;

    private final JobAlertService jobAlertService;
    private final UserService userService;
    private final PlanService planService;

    public SubscriberController(SubscriberService subscriberService, JobAlertService jobAlertService,
            UserService userService, PlanService planService) {
        this.planService = planService;
        this.subscriberService = subscriberService;
        this.jobAlertService = jobAlertService;
        this.userService = userService;
    }

    // how many skills the weekly alert may follow depends on the candidate's plan
    private void checkSkillCap(User me, Subscriber sub) throws PermissionException {
        int cap = this.planService.alertSkillCap(me.getId());
        if (sub.getSkills() != null && sub.getSkills().size() > cap) {
            throw new PermissionException("Gói " + PlanService.label(this.planService.activePlan(me.getId()))
                    + " cho phép tối đa " + cap + " kỹ năng nhận thông báo việc làm. Nâng cấp gói để chọn thêm.");
        }
    }

    private static boolean isSuperAdmin(User user) {
        return user.getRole() != null && "SUPER_ADMIN".equals(user.getRole().getName());
    }

    @PostMapping("/subscribers")
    @ApiMessage("Create a subscriber")
    public ResponseEntity<Subscriber> create(@Valid @RequestBody Subscriber sub)
            throws IdInvalidException, PermissionException {
        // anyone signed in may subscribe, but only their own address: otherwise this endpoint mails strangers
        User me = this.userService.handleGetCurrentUser();
        boolean admin = isSuperAdmin(me);
        if (!admin) {
            EmailVerificationService.requireVerified(me);
            sub.setEmail(me.getEmail());
            sub.setName(me.getName());
            checkSkillCap(me, sub);
        }
        // check email
        boolean isExist = this.subscriberService.isExistsByEmail(sub.getEmail());
        if (isExist == true) {
            throw new ConflictException("Email " + sub.getEmail() + " đã tồn tại");
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(this.subscriberService.create(sub));
    }

    @PutMapping("/subscribers")
    @ApiMessage("Update a subscriber")
    public ResponseEntity<Subscriber> update(@RequestBody Subscriber subsRequest)
            throws IdInvalidException, PermissionException {
        // check id
        Subscriber subsDB = this.subscriberService.findById(subsRequest.getId());
        if (subsDB == null) {
            throw new ResourceNotFoundException("Id " + subsRequest.getId() + " không tồn tại");
        }
        User me = this.userService.handleGetCurrentUser();
        boolean admin = isSuperAdmin(me);
        if (!admin && !subsDB.getEmail().equalsIgnoreCase(me.getEmail())) {
            throw new PermissionException("Bạn chỉ được sửa đăng ký nhận việc làm của chính mình.");
        }
        if (!admin) {
            checkSkillCap(me, subsRequest);
        }
        return ResponseEntity.ok().body(this.subscriberService.update(subsDB, subsRequest));
    }

    @GetMapping("/subscribers")
    @ApiMessage("Fetch subscribers with pagination")
    public ResponseEntity<ResultPaginationDTO> fetchAll(@Filter Specification<Subscriber> spec, Pageable pageable) {
        return ResponseEntity.ok(this.subscriberService.fetchAll(spec, pageable));
    }

    @GetMapping("/subscribers/{id}")
    @ApiMessage("Fetch a subscriber by id")
    public ResponseEntity<Subscriber> fetchById(@PathVariable("id") long id) throws IdInvalidException {
        Subscriber subs = this.subscriberService.findById(id);
        if (subs == null) {
            throw new ResourceNotFoundException("Id " + id + " không tồn tại");
        }
        return ResponseEntity.ok(subs);
    }

    @PutMapping("/subscribers/{id}")
    @ApiMessage("Update a subscriber by id")
    public ResponseEntity<Subscriber> updateById(@PathVariable("id") long id, @Valid @RequestBody Subscriber req)
            throws IdInvalidException {
        Subscriber subsDB = this.subscriberService.findById(id);
        if (subsDB == null) {
            throw new ResourceNotFoundException("Id " + id + " không tồn tại");
        }
        if (!subsDB.getEmail().equalsIgnoreCase(req.getEmail()) && this.subscriberService.isExistsByEmail(req.getEmail())) {
            throw new ConflictException("Email " + req.getEmail() + " đã tồn tại");
        }
        return ResponseEntity.ok(this.subscriberService.updateById(subsDB, req));
    }

    @DeleteMapping("/subscribers/{id}")
    @ApiMessage("Delete a subscriber")
    public ResponseEntity<Void> delete(@PathVariable("id") long id) throws IdInvalidException {
        if (this.subscriberService.findById(id) == null) {
            throw new ResourceNotFoundException("Id " + id + " không tồn tại");
        }
        this.subscriberService.delete(id);
        return ResponseEntity.ok().body(null);
    }

    // The link in the weekly digest (also called by mail apps' one-click button, which POSTs to this exact URL).
    @PostMapping("/subscribers/unsubscribe")
    @ApiMessage("Unsubscribe from the weekly digest")
    public ResponseEntity<Void> unsubscribe(@org.springframework.web.bind.annotation.RequestParam("token") String token)
            throws IdInvalidException {
        this.subscriberService.unsubscribe(token);
        return ResponseEntity.ok().body(null);
    }

    // Same run as the weekly schedule, on demand (admins only: SUBSCRIBERS permission).
    @PostMapping("/subscribers/send-digest")
    @ApiMessage("Send the job alert digest now")
    public ResponseEntity<java.util.Map<String, Integer>> sendDigest() {
        return ResponseEntity.ok(java.util.Map.of("sent", this.jobAlertService.sendDigest()));
    }

    @PostMapping("/subscribers/skills")
    @ApiMessage("Get subscriber's skill")
    public ResponseEntity<Subscriber> getSubscribersSkill() throws IdInvalidException {
        String email = SecurityUtil.getCurrentUserLogin().isPresent() == true
                ? SecurityUtil.getCurrentUserLogin().get()
                : "";

        return ResponseEntity.ok().body(this.subscriberService.findByEmail(email));
    }
}
