package vn.hoidanit.jobhunter.domain;

import jakarta.validation.constraints.Size;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import vn.hoidanit.jobhunter.util.SecurityUtil;
import vn.hoidanit.jobhunter.util.constant.ResumeStateEnum;

@Entity
@Table(name = "resumes", uniqueConstraints = @UniqueConstraint(columnNames = { "user_id", "job_id" }))
@Getter
@Setter
public class Resume {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    // Always set from the signed-in user by the server.
    private String email;

    @NotBlank(message = "url không được để trống (upload cv chưa thành công)")
    private String url;

    @Enumerated(EnumType.STRING)
    private ResumeStateEnum status;

    @Size(max = 3000, message = "Thư xin việc tối đa 3000 ký tự")
    @Column(columnDefinition = "TEXT")
    private String coverLetter;

    // Employer-side evaluation: never shown to the candidate.
    private Integer score;

    @Column(columnDefinition = "TEXT")
    private String remark;

    // Set when the application moves to INTERVIEW; shown to the candidate.
    private Instant interviewAt;

    @Column(length = 500)
    private String meetingLink;

    // Message the employer attaches to a decision (interview / accepted / rejected); shown to the candidate.
    @Column(columnDefinition = "TEXT")
    private String decisionNote;

    private Instant createdAt;
    private Instant updatedAt;

    private String createdBy;
    private String updatedBy;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    @JoinColumn(name = "job_id")
    private Job job;

    @PrePersist
    public void handleBeforeCreate() {
        this.createdBy = SecurityUtil.getCurrentUserLogin().isPresent() == true
                ? SecurityUtil.getCurrentUserLogin().get()
                : "";

        this.createdAt = Instant.now();
    }

    @PreUpdate
    public void handleBeforeUpdate() {
        this.updatedBy = SecurityUtil.getCurrentUserLogin().isPresent() == true
                ? SecurityUtil.getCurrentUserLogin().get()
                : "";

        this.updatedAt = Instant.now();
    }

}