package com.worksphere.dashboard.api;

import com.worksphere.auth.security.JwtPrincipal;
import com.worksphere.dashboard.service.DashboardService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public DashboardResponse summary(@AuthenticationPrincipal JwtPrincipal principal) {
        return dashboardService.summary(principal);
    }
}