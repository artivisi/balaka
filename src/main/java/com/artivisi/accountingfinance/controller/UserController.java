package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.util.FormUtils;
import com.artivisi.accountingfinance.enums.AuditEventType;
import com.artivisi.accountingfinance.enums.Role;
import com.artivisi.accountingfinance.security.PasswordValidator;
import com.artivisi.accountingfinance.security.Permission;
import com.artivisi.accountingfinance.service.DeviceAuthService;
import com.artivisi.accountingfinance.service.SecurityAuditService;
import com.artivisi.accountingfinance.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/users")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAuthority('" + Permission.USER_VIEW + "')")
public class UserController {

    private static final String ATTR_ROLES = "roles";
    private static final String ATTR_SELECTED_ROLES = "selectedRoles";
    private static final String ATTR_ERROR_MESSAGE = "errorMessage";
    private static final String ATTR_SUCCESS_MESSAGE = "successMessage";
    private static final String VIEW_FORM = "users/form";
    private static final String VIEW_CHANGE_PASSWORD = "users/change-password";
    private static final String REDIRECT_USERS = "redirect:/users";
    private static final String REDIRECT_USERS_PREFIX = "redirect:/users/";
    private static final String USER_NOT_FOUND = "User not found: ";
    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_INACTIVE = "inactive";
    private static final String AUDIT_ID_SUFFIX = " (id: ";

    private final UserService userService;
    private final PasswordValidator passwordValidator;
    private final SecurityAuditService securityAuditService;
    private final DeviceAuthService deviceAuthService;

    @Getter
    @Setter
    static class UserForm {
        private UUID id;

        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 100, message = "Username must be between 3 and 100 characters")
        private String username;

        @NotBlank(message = "Full name is required")
        @Size(max = 255, message = "Full name must not exceed 255 characters")
        private String fullName;

        @Email(message = "Email must be valid")
        @Size(max = 255, message = "Email must not exceed 255 characters")
        private String email;

        private Boolean active;
    }

    private User toEntity(UserForm form) {
        User entity = new User();
        BeanUtils.copyProperties(form, entity, "id", "active");
        entity.setActive(FormUtils.checkboxValue(form.getActive()));
        return entity;
    }

    private UserForm toForm(User entity) {
        UserForm form = new UserForm();
        BeanUtils.copyProperties(entity, form);
        return form;
    }

    @GetMapping
    public String list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            Model model) {

        Page<User> users = userService.search(search,
                PageRequest.of(page, size, Sort.by("username")));

        model.addAttribute("users", users);
        model.addAttribute("search", search);
        model.addAttribute(ATTR_ROLES, Role.values());

        return "users/list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('" + Permission.USER_CREATE + "')")
    public String newForm(Model model) {
        model.addAttribute("user", new UserForm());
        model.addAttribute(ATTR_ROLES, Role.values());
        model.addAttribute(ATTR_SELECTED_ROLES, new HashSet<>());
        return VIEW_FORM;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Permission.USER_CREATE + "')")
    public String create(
            @Valid @ModelAttribute("user") UserForm form,
            BindingResult bindingResult,
            @RequestParam(value = "selectedRoles", required = false) String[] selectedRoleNames,
            @RequestParam String password,
            RedirectAttributes redirectAttributes,
            Model model) {

        if (bindingResult.hasErrors()) {
            model.addAttribute(ATTR_ROLES, Role.values());
            model.addAttribute(ATTR_SELECTED_ROLES, selectedRoleNames != null ?
                    Arrays.stream(selectedRoleNames).map(Role::valueOf).collect(Collectors.toSet()) :
                    new HashSet<>());
            return VIEW_FORM;
        }

        try {
            Set<Role> roles = selectedRoleNames != null ?
                    Arrays.stream(selectedRoleNames).map(Role::valueOf).collect(Collectors.toSet()) :
                    new HashSet<>();

            if (roles.isEmpty()) {
                model.addAttribute(ATTR_ERROR_MESSAGE, "At least one role must be selected");
                model.addAttribute(ATTR_ROLES, Role.values());
                model.addAttribute(ATTR_SELECTED_ROLES, roles);
                return VIEW_FORM;
            }

            var validationResult = passwordValidator.validate(password);
            if (!validationResult.isValid()) {
                model.addAttribute(ATTR_ERROR_MESSAGE, validationResult.getErrorMessage());
                model.addAttribute(ATTR_ROLES, Role.values());
                model.addAttribute(ATTR_SELECTED_ROLES, roles);
                return VIEW_FORM;
            }

            User user = toEntity(form);
            user.setActive(true); // new users are always active
            user.setPassword(password);
            userService.create(user, roles);
            securityAuditService.log(AuditEventType.USER_CREATED,
                    "Created user: " + user.getUsername() + " with roles: " + roles);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Pengguna berhasil dibuat: " + user.getUsername());
            return REDIRECT_USERS;
        } catch (IllegalArgumentException e) {
            model.addAttribute(ATTR_ERROR_MESSAGE, e.getMessage());
            model.addAttribute(ATTR_ROLES, Role.values());
            model.addAttribute(ATTR_SELECTED_ROLES, selectedRoleNames != null ?
                    Arrays.stream(selectedRoleNames).map(Role::valueOf).collect(Collectors.toSet()) :
                    new HashSet<>());
            return VIEW_FORM;
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        User user = userService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(USER_NOT_FOUND + id));

        model.addAttribute("user", user);
        model.addAttribute("deviceTokens", deviceAuthService.getActiveTokens(user));
        return "users/detail";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('" + Permission.USER_EDIT + "')")
    public String editForm(@PathVariable UUID id, Model model) {
        User user = userService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(USER_NOT_FOUND + id));

        model.addAttribute("user", toForm(user));
        model.addAttribute(ATTR_ROLES, Role.values());
        model.addAttribute(ATTR_SELECTED_ROLES, user.getRoles());
        return VIEW_FORM;
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permission.USER_EDIT + "')")
    public String update(
            @PathVariable UUID id,
            @Valid @ModelAttribute("user") UserForm form,
            BindingResult bindingResult,
            @RequestParam(value = "selectedRoles", required = false) String[] selectedRoleNames,
            RedirectAttributes redirectAttributes,
            Model model) {

        if (bindingResult.hasErrors()) {
            model.addAttribute(ATTR_ROLES, Role.values());
            model.addAttribute(ATTR_SELECTED_ROLES, selectedRoleNames != null ?
                    Arrays.stream(selectedRoleNames).map(Role::valueOf).collect(Collectors.toSet()) :
                    new HashSet<>());
            return VIEW_FORM;
        }

        try {
            Set<Role> roles = selectedRoleNames != null ?
                    Arrays.stream(selectedRoleNames).map(Role::valueOf).collect(Collectors.toSet()) :
                    new HashSet<>();

            if (roles.isEmpty()) {
                model.addAttribute(ATTR_ERROR_MESSAGE, "At least one role must be selected");
                model.addAttribute(ATTR_ROLES, Role.values());
                model.addAttribute(ATTR_SELECTED_ROLES, roles);
                return VIEW_FORM;
            }

            User user = toEntity(form);
            userService.update(id, user, roles);
            securityAuditService.log(AuditEventType.USER_UPDATED,
                    "Updated user: " + user.getUsername() + AUDIT_ID_SUFFIX + id + ") with roles: " + roles);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Pengguna berhasil diperbarui: " + user.getUsername());
            return REDIRECT_USERS_PREFIX + id;
        } catch (IllegalArgumentException e) {
            model.addAttribute(ATTR_ERROR_MESSAGE, e.getMessage());
            model.addAttribute(ATTR_ROLES, Role.values());
            model.addAttribute(ATTR_SELECTED_ROLES, selectedRoleNames != null ?
                    Arrays.stream(selectedRoleNames).map(Role::valueOf).collect(Collectors.toSet()) :
                    new HashSet<>());
            return VIEW_FORM;
        }
    }

    @GetMapping("/{id}/change-password")
    @PreAuthorize("hasAuthority('" + Permission.USER_EDIT + "')")
    public String changePasswordForm(@PathVariable UUID id, Model model) {
        User user = userService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(USER_NOT_FOUND + id));

        model.addAttribute("user", user);
        return VIEW_CHANGE_PASSWORD;
    }

    @PostMapping("/{id}/change-password")
    @PreAuthorize("hasAuthority('" + Permission.USER_EDIT + "')")
    public String changePassword(
            @PathVariable UUID id,
            @RequestParam String newPassword,
            @RequestParam String confirmPassword,
            RedirectAttributes redirectAttributes,
            Model model) {

        User user = userService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(USER_NOT_FOUND + id));

        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("user", user);
            model.addAttribute(ATTR_ERROR_MESSAGE, "Password tidak cocok");
            return VIEW_CHANGE_PASSWORD;
        }

        var validationResult = passwordValidator.validate(newPassword);
        if (!validationResult.isValid()) {
            model.addAttribute("user", user);
            model.addAttribute(ATTR_ERROR_MESSAGE, validationResult.getErrorMessage());
            return VIEW_CHANGE_PASSWORD;
        }

        userService.changePassword(id, newPassword);
        securityAuditService.log(AuditEventType.PASSWORD_CHANGED,
                "Password changed for user: " + user.getUsername() + AUDIT_ID_SUFFIX + id + ")");
        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Password berhasil diubah");
        return REDIRECT_USERS_PREFIX + id;
    }

    @PostMapping("/{id}/toggle-active")
    @PreAuthorize("hasAuthority('" + Permission.USER_EDIT + "')")
    public String toggleActive(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        User user = userService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(USER_NOT_FOUND + id));
        Boolean wasActive = user.getActive();
        userService.toggleActive(id);
        String fromStatus = Boolean.TRUE.equals(wasActive) ? STATUS_ACTIVE : STATUS_INACTIVE;
        String toStatus = Boolean.TRUE.equals(wasActive) ? STATUS_INACTIVE : STATUS_ACTIVE;
        securityAuditService.log(AuditEventType.USER_STATUS_CHANGED,
                "User " + user.getUsername() + AUDIT_ID_SUFFIX + id + ") status changed from " +
                        fromStatus + " to " + toStatus);
        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Status pengguna berhasil diubah");
        return REDIRECT_USERS_PREFIX + id;
    }

    @PostMapping("/{id}/revoke-session/{tokenId}")
    @PreAuthorize("hasAuthority('" + Permission.USER_EDIT + "')")
    public String revokeSession(@PathVariable UUID id, @PathVariable UUID tokenId,
                                Authentication authentication, RedirectAttributes redirectAttributes) {
        User user = userService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(USER_NOT_FOUND + id));

        String revokedBy = authentication.getName();
        deviceAuthService.revokeToken(tokenId, revokedBy);
        securityAuditService.log(AuditEventType.USER_STATUS_CHANGED,
                "Revoked device session " + tokenId + " for user: " + user.getUsername() + " by " + revokedBy);
        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Sesi perangkat berhasil dicabut");
        return REDIRECT_USERS_PREFIX + id;
    }

    @PostMapping("/{id}/revoke-all-sessions")
    @PreAuthorize("hasAuthority('" + Permission.USER_EDIT + "')")
    public String revokeAllSessions(@PathVariable UUID id,
                                    Authentication authentication, RedirectAttributes redirectAttributes) {
        User user = userService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(USER_NOT_FOUND + id));

        String revokedBy = authentication.getName();
        int count = deviceAuthService.revokeAllTokens(user, revokedBy);
        securityAuditService.log(AuditEventType.USER_STATUS_CHANGED,
                "Revoked all device sessions (" + count + ") for user: " + user.getUsername() + " by " + revokedBy);
        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, count + " sesi perangkat berhasil dicabut");
        return REDIRECT_USERS_PREFIX + id;
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('" + Permission.USER_DELETE + "')")
    public String delete(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            User user = userService.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException(USER_NOT_FOUND + id));
            String username = user.getUsername();
            userService.delete(id);
            securityAuditService.log(AuditEventType.USER_DELETED,
                    "Deleted user: " + username + AUDIT_ID_SUFFIX + id + ")");
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Pengguna berhasil dihapus");
            return REDIRECT_USERS;
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute(ATTR_ERROR_MESSAGE, e.getMessage());
            return REDIRECT_USERS_PREFIX + id;
        }
    }
}
