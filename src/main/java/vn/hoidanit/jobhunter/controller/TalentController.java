package vn.hoidanit.jobhunter.controller;

import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import vn.hoidanit.jobhunter.domain.response.ResultPaginationDTO;
import vn.hoidanit.jobhunter.domain.response.profile.ResTalentDTO;
import vn.hoidanit.jobhunter.service.FileService;
import vn.hoidanit.jobhunter.service.TalentService;
import vn.hoidanit.jobhunter.util.annotation.ApiMessage;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;
import vn.hoidanit.jobhunter.util.error.PermissionException;
import vn.hoidanit.jobhunter.util.error.StorageException;

// Candidates who opted in, for SUPER_ADMIN and approved employers (permission TALENTS plus an approved company).
@RestController
@RequestMapping("/api/v1/talents")
public class TalentController {
    private final TalentService talentService;
    private final FileService fileService;

    public TalentController(TalentService talentService, FileService fileService) {
        this.talentService = talentService;
        this.fileService = fileService;
    }

    @GetMapping
    @ApiMessage("Search the talent directory")
    public ResponseEntity<ResultPaginationDTO> search(
            @RequestParam(required = false) String keyword, @RequestParam(required = false) String level,
            @RequestParam(required = false) String industry, @RequestParam(required = false) String occupation,
            @RequestParam(required = false) String experience, @RequestParam(required = false) List<String> skills,
            Pageable pageable) throws IdInvalidException, PermissionException {
        return ResponseEntity.ok(this.talentService.search(keyword, level, industry, occupation, experience, skills, pageable));
    }

    @GetMapping("/{id}")
    @ApiMessage("Fetch a candidate from the talent directory")
    public ResponseEntity<ResTalentDTO.Detail> detail(@PathVariable("id") long id) throws IdInvalidException, PermissionException {
        return ResponseEntity.ok(this.talentService.detail(id));
    }

    @GetMapping("/{id}/cv")
    @ApiMessage("Stream a candidate's CV")
    public ResponseEntity<Resource> cv(@PathVariable("id") long id) throws IdInvalidException, PermissionException, StorageException {
        return this.fileService.serveResume(this.talentService.cvOf(id));
    }
}
