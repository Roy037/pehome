package vn.hoidanit.jobhunter.domain;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import vn.hoidanit.jobhunter.util.SecurityUtil;
import vn.hoidanit.jobhunter.util.constant.CompanyTypeEnum;

@Table(name = "companies")
@Entity
@Getter
@Setter
public class Company {
//    asd

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @NotBlank(message = "name không được để trống")
    private String name;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String description;
    private String address;
    private String logo;
    @Size(max = 255, message = "banner tối đa 255 ký tự")
    @Pattern(regexp = "^[^/\\\\?#]*$", message = "banner không hợp lệ")
    private String banner;

    @Size(max = 255, message = "website tối đa 255 ký tự")
    @Pattern(regexp = "^(https?://\\S+)?$", message = "website phải bắt đầu bằng http:// hoặc https://")
    private String website;

    // Only Google Maps embed URLs are accepted because the page renders this value as an iframe src.
    @Size(max = 1000, message = "Liên kết bản đồ tối đa 1000 ký tự")
    @Pattern(regexp = "^(https://www\\.google\\.com/maps/embed(/v1/\\w+)?\\?[^\\s\"'<>]+|https://maps\\.google\\.com/maps\\?[^\\s\"'<>]*output=embed[^\\s\"'<>]*)?$",
            message = "Liên kết bản đồ phải là URL nhúng của Google Maps (Chia sẻ > Nhúng bản đồ)")
    @Column(length = 1000)
    private String mapEmbedUrl;

    @Enumerated(EnumType.STRING)
    private CompanyTypeEnum companyType;

    // Each social link is pinned to its own network's host: the page shows it under that network's icon.
    private static final String SOCIAL_TAIL = "(/\\S*)?)?$";

    @Size(max = 255, message = "Liên kết Facebook tối đa 255 ký tự")
    @Pattern(regexp = "^(https://(www\\.)?facebook\\.com" + SOCIAL_TAIL, message = "Liên kết Facebook không hợp lệ (cần https://…)")
    private String facebookUrl;

    @Size(max = 255, message = "Liên kết LinkedIn tối đa 255 ký tự")
    @Pattern(regexp = "^(https://(www\\.)?linkedin\\.com" + SOCIAL_TAIL, message = "Liên kết LinkedIn không hợp lệ (cần https://…)")
    private String linkedinUrl;

    @Size(max = 255, message = "Liên kết Twitter/X tối đa 255 ký tự")
    @Pattern(regexp = "^(https://(www\\.)?(twitter|x)\\.com" + SOCIAL_TAIL, message = "Liên kết Twitter/X không hợp lệ (cần https://…)")
    private String twitterUrl;

    @Size(max = 255, message = "Liên kết Pinterest tối đa 255 ký tự")
    @Pattern(regexp = "^(https://(www\\.)?pinterest\\.com" + SOCIAL_TAIL, message = "Liên kết Pinterest không hợp lệ (cần https://…)")
    private String pinterestUrl;

    @Size(max = 255, message = "Liên kết Instagram tối đa 255 ký tự")
    @Pattern(regexp = "^(https://(www\\.)?instagram\\.com" + SOCIAL_TAIL, message = "Liên kết Instagram không hợp lệ (cần https://…)")
    private String instagramUrl;

    @Size(max = 255, message = "Liên kết YouTube tối đa 255 ký tự")
    @Pattern(regexp = "^(https://(www\\.)?(youtube\\.com|youtu\\.be)" + SOCIAL_TAIL, message = "Liên kết YouTube không hợp lệ (cần https://…)")
    private String youtubeUrl;

    // Self-registered employers start unapproved; the column default keeps existing companies approved.
    @Column(columnDefinition = "bit(1) not null default 1")
    private boolean approved = true;
    // set when an admin rejects the company; the employer sees it and editing the profile sends it back for review
    @Size(max = 500)
    private String rejectionReason;
    private Instant rejectedAt;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
    private String updatedBy;
    @OneToMany(mappedBy = "company", fetch = FetchType.LAZY)
    @JsonIgnore
    List<User> users;
    @OneToMany(mappedBy = "company", fetch = FetchType.LAZY)
    @JsonIgnore
    List<Job> jobs;

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
