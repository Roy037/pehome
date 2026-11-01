package vn.hoidanit.jobhunter.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import vn.hoidanit.jobhunter.domain.Company;
import vn.hoidanit.jobhunter.service.CompanyFollowService;
import vn.hoidanit.jobhunter.util.annotation.ApiMessage;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;
import vn.hoidanit.jobhunter.util.error.PermissionException;

@RestController
@RequestMapping("/api/v1")
public class CompanyFollowController {

    private final CompanyFollowService followService;

    public CompanyFollowController(CompanyFollowService followService) {
        this.followService = followService;
    }

    @GetMapping("/me/followed-companies")
    @ApiMessage("Fetch companies I follow")
    public ResponseEntity<List<Company>> fetchMine() throws IdInvalidException {
        return ResponseEntity.ok(this.followService.fetchMine());
    }

    @PutMapping("/me/followed-companies/{companyId}")
    @ApiMessage("Follow a company")
    public ResponseEntity<Void> follow(@PathVariable("companyId") long companyId) throws IdInvalidException, PermissionException {
        this.followService.follow(companyId);
        return ResponseEntity.ok().body(null);
    }

    @DeleteMapping("/me/followed-companies/{companyId}")
    @ApiMessage("Unfollow a company")
    public ResponseEntity<Void> unfollow(@PathVariable("companyId") long companyId) throws IdInvalidException {
        this.followService.unfollow(companyId);
        return ResponseEntity.ok().body(null);
    }
}
