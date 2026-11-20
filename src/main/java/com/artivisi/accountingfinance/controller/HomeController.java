package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.security.Permission;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Landing page after login, after a forced password change and after the MFA step. The dashboard needs
 * DASHBOARD_VIEW, which every role has except EMPLOYEE; an EMPLOYEE only has the self-service pages.
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public String home(Authentication authentication) {
        Set<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        if (authorities.contains(Permission.DASHBOARD_VIEW)) {
            return "redirect:/dashboard";
        }
        if (authorities.contains(Permission.OWN_PAYSLIP_VIEW)) {
            return "redirect:/self-service/payslips";
        }
        throw new AccessDeniedException("User " + authentication.getName()
                + " has no role with a landing page (dashboard or self-service)");
    }
}
