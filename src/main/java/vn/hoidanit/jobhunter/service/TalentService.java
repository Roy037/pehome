package vn.hoidanit.jobhunter.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import vn.hoidanit.jobhunter.domain.CandidateProfile;
import vn.hoidanit.jobhunter.domain.Company;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.domain.response.ResultPaginationDTO;
import vn.hoidanit.jobhunter.domain.response.profile.ResTalentDTO;
import vn.hoidanit.jobhunter.repository.CandidateProfileRepository;
import vn.hoidanit.jobhunter.util.constant.LevelEnum;
import vn.hoidanit.jobhunter.util.constant.PlanEnum;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;
import vn.hoidanit.jobhunter.util.error.PermissionException;
import vn.hoidanit.jobhunter.util.error.ResourceNotFoundException;

/**
 * Directory of candidates who opted in (CandidateProfile.visibleToEmployers). Only SUPER_ADMIN and employers of an
 * approved company may browse it. The search takes plain parameters instead of a free-form spring-filter expression, so
 * it can never be steered at fields a candidate did not share (references, other users' data).
 */
@Service
@Transactional(readOnly = true)
public class TalentService {
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_SKILLS = 10;

    private final CandidateProfileRepository profileRepository;
    private final UserService userService;
    private final PlanService planService;

    public TalentService(CandidateProfileRepository profileRepository, UserService userService, PlanService planService) {
        this.profileRepository = profileRepository;
        this.userService = userService;
        this.planService = planService;
    }

    private void assertCanBrowse() throws IdInvalidException, PermissionException {
        User me = this.userService.handleGetCurrentUser();
        Company company = me.getCompany();
        if (!UserService.isSuperAdmin(me) && (company == null || !company.isApproved())) {
            throw new PermissionException("Chỉ nhà tuyển dụng đã được duyệt mới xem được kho ứng viên.");
        }
    }

    // The list is free to browse; opening a profile or a CV needs the talent directory unlocked for the company.
    private void assertUnlocked() throws IdInvalidException, PermissionException {
        User me = this.userService.handleGetCurrentUser();
        if (UserService.isSuperAdmin(me)) {
            return;
        }
        if (this.planService.talentUnlockedUntil(me.getCompany().getId(), Instant.now()) == null) {
            throw new PermissionException("Hãy mở khóa kho ứng viên (mục Dịch vụ) để xem chi tiết hồ sơ và CV.");
        }
    }

    private static Specification<CandidateProfile> optedIn() {
        return (root, query, cb) -> cb.and(
                cb.isTrue(root.get("visibleToEmployers")),
                cb.isFalse(root.get("user").get("locked")),
                cb.isNull(root.get("user").get("company")));
    }

    private static String like(String text) {
        return "%" + text.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    public ResultPaginationDTO search(String keyword, String level, String industry, String occupation, String experience,
            List<String> skills, Pageable pageable) throws IdInvalidException, PermissionException {
        assertCanBrowse();
        LevelEnum wantedLevel = null;
        if (level != null && !level.isBlank()) {
            try {
                wantedLevel = LevelEnum.valueOf(level.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IdInvalidException("Cấp độ không hợp lệ.");
            }
        }
        final LevelEnum lv = wantedLevel;
        List<String> wantedSkills = skills == null ? List.of()
                : skills.stream().filter(s -> s != null && !s.isBlank()).map(s -> s.trim().toLowerCase()).distinct().limit(MAX_SKILLS).toList();

        Specification<CandidateProfile> spec = optedIn().and((root, query, cb) -> {
            query.distinct(true);
            List<Predicate> all = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                String pattern = like(keyword.length() > 100 ? keyword.substring(0, 100) : keyword);
                all.add(cb.or(
                        cb.like(cb.lower(root.get("user").get("name")), pattern, '\\'),
                        cb.like(cb.lower(root.get("headline")), pattern, '\\'),
                        cb.like(cb.lower(root.get("occupation")), pattern, '\\'),
                        cb.like(cb.lower(root.get("industry")), pattern, '\\')));
            }
            if (lv != null) {
                all.add(cb.equal(root.get("level"), lv));
            }
            if (industry != null && !industry.isBlank()) {
                all.add(cb.equal(cb.lower(root.get("industry")), industry.trim().toLowerCase()));
            }
            if (occupation != null && !occupation.isBlank()) {
                all.add(cb.equal(cb.lower(root.get("occupation")), occupation.trim().toLowerCase()));
            }
            if (experience != null && !experience.isBlank()) {
                all.add(cb.equal(root.get("experience"), experience.trim()));
            }
            // every requested skill must be on the profile: one join per skill
            for (String skill : wantedSkills) {
                Join<CandidateProfile, CandidateProfile.Skill> join = root.join("skills");
                all.add(cb.equal(cb.lower(join.get("name")), skill));
            }
            return cb.and(all.toArray(new Predicate[0]));
        });

        Pageable page = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<CandidateProfile> found = this.profileRepository.findAll(spec, page);
        Map<Long, PlanEnum> plans = this.planService.activePlans(found.getContent().stream().map(p -> p.getUser().getId()).toList());

        ResultPaginationDTO rs = new ResultPaginationDTO();
        ResultPaginationDTO.Meta meta = new ResultPaginationDTO.Meta();
        meta.setPage(page.getPageNumber() + 1);
        meta.setPageSize(page.getPageSize());
        meta.setPages(found.getTotalPages());
        meta.setTotal(found.getTotalElements());
        rs.setMeta(meta);
        rs.setResult(found.getContent().stream().map(p -> toSummary(p, plans.get(p.getUser().getId()))).toList());
        return rs;
    }

    public ResTalentDTO.Detail detail(long id) throws IdInvalidException, PermissionException {
        CandidateProfile profile = find(id);
        assertUnlocked();
        PlanEnum plan = this.planService.activePlan(profile.getUser().getId());
        return new ResTalentDTO.Detail(toSummary(profile, plan), profile.getUser().getEmail(),
                new ArrayList<>(profile.getShortGoals()), new ArrayList<>(profile.getLongGoals()),
                new ArrayList<>(profile.getExperiences()), profile.getCvName());
    }

    /** Stored file name of the opted-in candidate's CV. */
    public String cvOf(long id) throws IdInvalidException, PermissionException {
        String cv = find(id).getCvUrl();
        assertUnlocked();
        if (cv == null) {
            throw new ResourceNotFoundException("Ứng viên chưa tải CV lên hồ sơ.");
        }
        return cv;
    }

    private CandidateProfile find(long id) throws IdInvalidException, PermissionException {
        assertCanBrowse();
        return this.profileRepository.findOne(optedIn().and((root, query, cb) -> cb.equal(root.get("id"), id)))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ ứng viên."));
    }

    private static ResTalentDTO toSummary(CandidateProfile p, PlanEnum plan) {
        User u = p.getUser();
        return new ResTalentDTO(p.getId(), u.getName(), u.getAvatar(), p.getHeadline(), p.getExperience(), p.getLevel(),
                p.getIndustry(), p.getOccupation(), new ArrayList<>(p.getSkills()), plan != null && plan.isHighlight(),
                p.getCvUrl() != null, p.getUpdatedAt());
    }
}
