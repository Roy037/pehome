package vn.hoidanit.jobhunter.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.hoidanit.jobhunter.domain.CandidateProfile;
import vn.hoidanit.jobhunter.domain.Subscriber;
import vn.hoidanit.jobhunter.domain.User;
import vn.hoidanit.jobhunter.domain.request.ReqProfileDTO;
import vn.hoidanit.jobhunter.domain.response.profile.ResProfileDTO;
import vn.hoidanit.jobhunter.repository.CandidateProfileRepository;
import vn.hoidanit.jobhunter.repository.SubscriberRepository;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;

@Service
@Transactional
public class ProfileService {

    private final CandidateProfileRepository profileRepository;
    private final SubscriberRepository subscriberRepository;
    private final UserService userService;

    public ProfileService(CandidateProfileRepository profileRepository, SubscriberRepository subscriberRepository,
            UserService userService) {
        this.profileRepository = profileRepository;
        this.subscriberRepository = subscriberRepository;
        this.userService = userService;
    }


    @Transactional(readOnly = true)
    public ResProfileDTO getMine() throws IdInvalidException {
        User user = this.userService.handleGetCurrentUser();
        return this.profileRepository.findByUserId(user.getId())
                .map(profile -> toRes(profile, user))
                .orElseGet(() -> toRes(new CandidateProfile(), user));
    }

    /** Just the profile picture, for accounts that have no candidate profile page (employers, admins). Blank removes it. */
    public void saveAvatar(String avatar) throws IdInvalidException {
        User user = this.userService.handleGetCurrentUser();
        String file = blankToNull(avatar);
        if (file != null && !FileService.isUploadedBy(file, user.getId())) {
            throw new IdInvalidException("Ảnh đại diện không hợp lệ.");
        }
        user.setAvatar(file);
    }

    public ResProfileDTO saveMine(ReqProfileDTO req) throws IdInvalidException {
        User user = this.userService.handleGetCurrentUser();
        CandidateProfile profile = this.profileRepository.findByUserId(user.getId()).orElseGet(() -> {
            CandidateProfile created = new CandidateProfile();
            created.setUser(user);
            return created;
        });

        if (req.getName() != null && !req.getName().isBlank()) {
            user.setName(req.getName().trim());
        }
        user.setAvatar(blankToNull(req.getAvatar()));
        user.setAge(req.getAge() == null ? 0 : req.getAge());
        user.setGender(req.getGender());
        user.setAddress(blankToNull(req.getAddress()));
        profile.setHeadline(blankToNull(req.getHeadline()));
        profile.setExperience(blankToNull(req.getExperience()));
        profile.setLevel(req.getLevel());
        profile.setIndustry(blankToNull(req.getIndustry()));
        profile.setOccupation(blankToNull(req.getOccupation()));
        profile.setJobAlert(req.isJobAlert());
        // employers and admins are not candidates, so they can never be listed
        profile.setVisibleToEmployers(req.isVisibleToEmployers() && user.getCompany() == null && !UserService.isSuperAdmin(user));
        if (!req.isJobAlert()) {
            // switching alerts off must actually stop the weekly e-mail
            Subscriber subscription = this.subscriberRepository.findByEmail(user.getEmail());
            if (subscription != null) {
                this.subscriberRepository.delete(subscription);
            }
        }

        replace(profile.getShortGoals(), trimmed(req.getShortGoals()));
        replace(profile.getLongGoals(), trimmed(req.getLongGoals()));
        replace(profile.getExperiences(), req.getExperiences());
        replace(profile.getSkills(), req.getSkills());
        replace(profile.getReferences(), req.getReferences());

        String cvUrl = blankToNull(req.getCvUrl());
        if (cvUrl != null && !cvUrl.equals(profile.getCvUrl()) && !FileService.isUploadedBy(cvUrl, user.getId())) {
            throw new IdInvalidException("Tệp CV không hợp lệ.");
        }
        if (cvUrl == null) {
            profile.setCvUrl(null);
            profile.setCvName(null);
            profile.setCvUpdatedAt(null);
        } else {
            if (!cvUrl.equals(profile.getCvUrl())) {
                profile.setCvUpdatedAt(Instant.now());
            }
            profile.setCvUrl(cvUrl);
            profile.setCvName(blankToNull(req.getCvName()));
        }

        return toRes(this.profileRepository.save(profile), user);
    }

    private static <T> void replace(List<T> target, List<T> source) {
        target.clear();
        if (source != null) {
            target.addAll(source);
        }
    }

    private static List<String> trimmed(List<String> values) {
        List<String> out = new ArrayList<>();
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    out.add(value.trim());
                }
            }
        }
        return out;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ResProfileDTO toRes(CandidateProfile profile, User user) {
        ResProfileDTO res = new ResProfileDTO();
        res.setName(user.getName());
        res.setEmail(user.getEmail());
        res.setAvatar(user.getAvatar());
        res.setAge(user.getAge() > 0 ? user.getAge() : null);
        res.setGender(user.getGender());
        res.setAddress(user.getAddress());
        res.setHeadline(profile.getHeadline());
        res.setExperience(profile.getExperience());
        res.setLevel(profile.getLevel());
        res.setIndustry(profile.getIndustry());
        res.setOccupation(profile.getOccupation());
        // "on" also when the user subscribed from the e-mail tab or the footer without ever touching this switch
        Subscriber subscription = this.subscriberRepository.findByEmail(user.getEmail());
        boolean subscribed = subscription != null && subscription.getSkills() != null && !subscription.getSkills().isEmpty();
        res.setJobAlert(profile.isJobAlert() || subscribed);
        res.setVisibleToEmployers(profile.isVisibleToEmployers());
        res.setShortGoals(new ArrayList<>(profile.getShortGoals()));
        res.setLongGoals(new ArrayList<>(profile.getLongGoals()));
        res.setExperiences(new ArrayList<>(profile.getExperiences()));
        res.setSkills(new ArrayList<>(profile.getSkills()));
        res.setReferences(new ArrayList<>(profile.getReferences()));
        res.setCvUrl(profile.getCvUrl());
        res.setCvName(profile.getCvName());
        res.setCvUpdatedAt(profile.getCvUpdatedAt());
        res.setUpdatedAt(profile.getUpdatedAt());
        return res;
    }
}
