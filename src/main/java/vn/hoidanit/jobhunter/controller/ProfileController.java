package vn.hoidanit.jobhunter.controller;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import vn.hoidanit.jobhunter.domain.request.ReqAvatarDTO;
import vn.hoidanit.jobhunter.domain.request.ReqProfileDTO;
import vn.hoidanit.jobhunter.domain.response.profile.ResProfileDTO;
import vn.hoidanit.jobhunter.service.FileService;
import vn.hoidanit.jobhunter.service.ProfileService;
import vn.hoidanit.jobhunter.util.annotation.ApiMessage;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;
import vn.hoidanit.jobhunter.util.error.StorageException;

@RestController
@RequestMapping("/api/v1/me/profile")
public class ProfileController {

    private final ProfileService profileService;
    private final FileService fileService;

    public ProfileController(ProfileService profileService, FileService fileService) {
        this.profileService = profileService;
        this.fileService = fileService;
    }

    // The CV kept in my profile, streamed to me only (CV files are not publicly served).
    @GetMapping("/cv")
    @ApiMessage("Stream my profile CV")
    public ResponseEntity<Resource> myCv() throws IdInvalidException, StorageException {
        String stored = this.profileService.getMine().getCvUrl();
        if (stored == null) {
            throw new StorageException("Bạn chưa tải CV lên hồ sơ.");
        }
        return this.fileService.serveResume(stored);
    }

    @GetMapping
    @ApiMessage("Fetch my candidate profile")
    public ResponseEntity<ResProfileDTO> fetchMine() throws IdInvalidException {
        return ResponseEntity.ok(this.profileService.getMine());
    }

    // Works for every signed-in account (employers and admins have no candidate profile page).
    @PutMapping("/avatar")
    @ApiMessage("Change my profile picture")
    public ResponseEntity<Void> saveAvatar(@Valid @RequestBody ReqAvatarDTO req) throws IdInvalidException {
        this.profileService.saveAvatar(req.getAvatar());
        return ResponseEntity.ok().body(null);
    }

    @PutMapping
    @ApiMessage("Save my candidate profile")
    public ResponseEntity<ResProfileDTO> saveMine(@Valid @RequestBody ReqProfileDTO req) throws IdInvalidException {
        return ResponseEntity.ok(this.profileService.saveMine(req));
    }
}
