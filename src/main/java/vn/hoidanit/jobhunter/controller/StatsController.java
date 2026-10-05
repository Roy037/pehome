package vn.hoidanit.jobhunter.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import vn.hoidanit.jobhunter.domain.response.ResAdminStatsDTO;
import vn.hoidanit.jobhunter.service.StatsService;
import vn.hoidanit.jobhunter.util.annotation.ApiMessage;

@RestController
@RequestMapping("/api/v1")
public class StatsController {
    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    // SUPER_ADMIN only (permission STATS), so the numbers are platform-wide by design.
    @GetMapping("/admin/stats")
    @ApiMessage("Platform statistics")
    public ResponseEntity<ResAdminStatsDTO> stats() {
        return ResponseEntity.ok(this.statsService.overview());
    }
}
