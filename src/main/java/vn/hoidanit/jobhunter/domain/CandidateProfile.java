package vn.hoidanit.jobhunter.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import vn.hoidanit.jobhunter.util.constant.LevelEnum;

// One profile per candidate. The list-like sections live in collection tables (FK to this row, cascade on delete).
@Entity
@Table(name = "candidate_profiles")
@Getter
@Setter
public class CandidateProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "user_id", unique = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    private String headline;
    private String experience;

    @Enumerated(EnumType.STRING)
    private LevelEnum level;

    private String industry;
    private String occupation;
    private boolean jobAlert;

    // opt-in: only profiles with this on can be found by approved employers
    @Column(columnDefinition = "bit(1) not null default 0")
    private boolean visibleToEmployers;

    private String cvUrl;
    private String cvName;
    private Instant cvUpdatedAt;

    @ElementCollection
    @CollectionTable(name = "profile_short_goals", joinColumns = @JoinColumn(name = "profile_id",
            foreignKey = @ForeignKey(name = "fk_profile_short_goals", foreignKeyDefinition = "foreign key (profile_id) references candidate_profiles (id) on delete cascade")))
    @OrderColumn(name = "sort_order")
    @Column(name = "goal", length = 300)
    private List<String> shortGoals = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "profile_long_goals", joinColumns = @JoinColumn(name = "profile_id",
            foreignKey = @ForeignKey(name = "fk_profile_long_goals", foreignKeyDefinition = "foreign key (profile_id) references candidate_profiles (id) on delete cascade")))
    @OrderColumn(name = "sort_order")
    @Column(name = "goal", length = 300)
    private List<String> longGoals = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "profile_experiences", joinColumns = @JoinColumn(name = "profile_id",
            foreignKey = @ForeignKey(name = "fk_profile_experiences", foreignKeyDefinition = "foreign key (profile_id) references candidate_profiles (id) on delete cascade")))
    @OrderColumn(name = "sort_order")
    private List<Experience> experiences = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "profile_skills", joinColumns = @JoinColumn(name = "profile_id",
            foreignKey = @ForeignKey(name = "fk_profile_skills", foreignKeyDefinition = "foreign key (profile_id) references candidate_profiles (id) on delete cascade")))
    @OrderColumn(name = "sort_order")
    private List<Skill> skills = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "profile_references", joinColumns = @JoinColumn(name = "profile_id",
            foreignKey = @ForeignKey(name = "fk_profile_references", foreignKeyDefinition = "foreign key (profile_id) references candidate_profiles (id) on delete cascade")))
    @OrderColumn(name = "sort_order")
    private List<Reference> references = new ArrayList<>();

    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    public void touch() {
        this.updatedAt = Instant.now();
    }

    @Embeddable
    @Getter
    @Setter
    public static class Experience {
        @NotBlank(message = "Tên công ty không được để trống")
        @Size(max = 150, message = "Tên công ty tối đa 150 ký tự")
        private String company;

        @NotBlank(message = "Vị trí không được để trống")
        @Size(max = 150, message = "Vị trí tối đa 150 ký tự")
        private String title;

        @Pattern(regexp = "^\\d{4}-\\d{2}$", message = "Tháng bắt đầu phải có dạng yyyy-MM")
        private String fromMonth;

        @Pattern(regexp = "^(\\d{4}-\\d{2})?$", message = "Tháng kết thúc phải có dạng yyyy-MM")
        private String toMonth;

        @Column(name = "is_current")
        private boolean current;

        @Size(max = 2000, message = "Mô tả tối đa 2000 ký tự")
        @Column(length = 2000)
        private String description;
    }

    @Embeddable
    @Getter
    @Setter
    public static class Skill {
        @NotBlank(message = "Tên kỹ năng không được để trống")
        @Size(max = 60, message = "Tên kỹ năng tối đa 60 ký tự")
        private String name;

        @Min(value = 1, message = "Mức kỹ năng từ 1 đến 5")
        @Max(value = 5, message = "Mức kỹ năng từ 1 đến 5")
        @Column(name = "skill_level")
        private int level;
    }

    @Embeddable
    @Getter
    @Setter
    public static class Reference {
        @NotBlank(message = "Tên người tham khảo không được để trống")
        @Size(max = 100, message = "Tên tối đa 100 ký tự")
        private String name;

        @Size(max = 100, message = "Chức vụ tối đa 100 ký tự")
        private String title;

        @Size(max = 100, message = "Công ty tối đa 100 ký tự")
        private String company;

        @Size(max = 30, message = "Số điện thoại tối đa 30 ký tự")
        private String phone;

        @Email(message = "Email người tham khảo chưa hợp lệ")
        @Size(max = 120, message = "Email tối đa 120 ký tự")
        private String email;
    }
}
