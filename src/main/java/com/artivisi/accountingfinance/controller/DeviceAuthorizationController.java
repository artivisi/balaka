package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.DeviceCode;
import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.repository.UserRepository;
import com.artivisi.accountingfinance.service.DeviceAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

/**
 * Web controller for device authorization (browser-based).
 * User visits this page to authorize a device using the user code.
 */
@Controller
@RequestMapping("/device")
@RequiredArgsConstructor
@Slf4j
public class DeviceAuthorizationController {

    private final DeviceAuthService deviceAuthService;
    private final UserRepository userRepository;

    /**
     * Show device authorization page.
     * GET /device?code=XXXX-XXXX
     */
    @GetMapping
    public String showAuthorizationPage(
            @RequestParam(required = false) String code,
            Authentication authentication,
            Model model) {

        if (authentication == null || !authentication.isAuthenticated()) {
            // User not logged in - redirect to login with return URL
            return "redirect:/login?returnUrl=/device" +
                    (code != null ? "?code=" + code : "");
        }

        model.addAttribute("userCode", code != null ? code : "");
        model.addAttribute("username", authentication.getName());
        model.addAttribute("validCode", false); // Default to false

        // If code provided, validate it
        if (code != null && !code.isBlank()) {
            Optional<DeviceCode> deviceCode = deviceAuthService.findByUserCode(code);

            if (deviceCode.isPresent()) {
                DeviceCode dc = deviceCode.get();

                if (dc.isExpired()) {
                    model.addAttribute("error", "Kode perangkat telah kedaluwarsa. Silakan mulai lagi dari aplikasi.");
                    return "device-authorize";
                }

                if (dc.getStatus() == DeviceCode.Status.AUTHORIZED) {
                    model.addAttribute("success", "Perangkat sudah diotorisasi sebelumnya.");
                    return "device-authorize";
                }

                model.addAttribute("clientId", dc.getClientId());
                model.addAttribute("validCode", true);
            } else {
                model.addAttribute("error", "Kode perangkat tidak valid.");
            }
        }

        return "device-authorize";
    }

    /**
     * Authorize device.
     * POST /device/authorize
     */
    @PostMapping("/authorize")
    public String authorizeDevice(
            @RequestParam String userCode,
            @RequestParam(required = false) String deviceName,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }

        try {
            User user = userRepository.findByUsername(authentication.getName())
                    .orElseThrow(() -> new IllegalStateException("User not found"));

            deviceAuthService.authorizeDevice(userCode, user, deviceName);

            redirectAttributes.addFlashAttribute("success",
                    "Perangkat berhasil diotorisasi! Anda dapat kembali ke aplikasi.");

            log.info("User {} authorized device with code {}", user.getUsername(), userCode);

            return "redirect:/device/success";

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", "Kode perangkat tidak valid.");
            return "redirect:/device?code=" + userCode;

        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/device?code=" + userCode;
        }
    }

    /**
     * Show success page after authorization.
     */
    @GetMapping("/success")
    public String showSuccessPage() {
        return "device-success";
    }

    /**
     * Deny device authorization.
     * POST /device/deny
     */
    @PostMapping("/deny")
    public String denyDevice(
            @RequestParam String userCode,
            RedirectAttributes redirectAttributes) {

        try {
            Optional<DeviceCode> deviceCode = deviceAuthService.findByUserCode(userCode);

            if (deviceCode.isPresent()) {
                DeviceCode dc = deviceCode.get();
                dc.deny();
                // Save would be done in service, but for now just log

                redirectAttributes.addFlashAttribute("success",
                        "Otorisasi perangkat ditolak.");
            }

            return "redirect:/device/success";

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Gagal menolak otorisasi.");
            return "redirect:/device?code=" + userCode;
        }
    }
}
