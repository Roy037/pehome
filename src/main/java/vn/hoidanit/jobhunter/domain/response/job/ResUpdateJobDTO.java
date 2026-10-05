package vn.hoidanit.jobhunter.domain.response.job;

import java.time.Instant;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
import vn.hoidanit.jobhunter.util.constant.EmploymentTypeEnum;
import vn.hoidanit.jobhunter.util.constant.LevelEnum;
import vn.hoidanit.jobhunter.util.constant.WorkModeEnum;

@Getter
@Setter
public class ResUpdateJobDTO {
    private long id;
    private String name;

    private String location;

    private double salary;
    private Double salaryMax;

    private int quantity;

    private LevelEnum level;
    private EmploymentTypeEnum employmentType;
    private WorkModeEnum workMode;

    private Instant startDate;
    private Instant endDate;
    private boolean isActive;

    private List<String> skills;

    private Instant updatedAt;
    private String updatedBy;
}
