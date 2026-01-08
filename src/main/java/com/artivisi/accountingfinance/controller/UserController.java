package com.artivisi.accountingfinance.controller;

import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.enums.AuditEventType;
import com.artivisi.accountingfinance.enums.Role;
import com.artivisi.accountingfinance.security.PasswordValidator;
import com.artivisi.accountingfinance.security.Permission;
import com.artivisi.accountingfinance.service.SecurityAuditService;
import com.artivisi.accountingfinance.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
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
    private static final String USER_NOT_FOUND = "User not found: ";
    private static final String AUDIT_ID_SUFFIX = " (id: ";

    private final UserService userService;
    private final PasswordValidator passwordValidator;
    private final SecurityAuditService securityAuditService;

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
        model.addAttribute("user", new User());
        model.addAttribute(ATTR_ROLES, Role.values());
        model.addAttribute(ATTR_SELECTED_ROLES, new HashSet<>());
        return VIEW_FORM;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Permission.USER_CREATE + "')")
    public String create(
            @Valid @ModelAttribute User user,
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
        return "users/detail";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('" + Permission.USER_EDIT + "')")
    public String editForm(@PathVariable UUID id, Model model) {
        User user = userService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(USER_NOT_FOUND + id));

        model.addAttribute("user", user);
        model.addAttribute(ATTR_ROLES, Role.values());
        model.addAttribute(ATTR_SELECTED_ROLES, user.getRoles());
        return VIEW_FORM;
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permission.USER_EDIT + "')")
    public String update(
            @PathVariable UUID id,
            @Valid @ModelAttribute User user,
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

            userService.update(id, user, roles);
            securityAuditService.log(AuditEventType.USER_UPDATED,
                    "Updated user: " + user.getUsername() + AUDIT_ID_SUFFIX + id + ") with roles: " + roles);
            redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Pengguna berhasil diperbarui: " + user.getUsername());
            return "redirect:/users/" + id;
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
        return "redirect:/users/" + id;
    }

    @PostMapping("/{id}/toggle-active")
    @PreAuthorize("hasAuthority('" + Permission.USER_EDIT + "')")
    public String toggleActive(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        User user = userService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(USER_NOT_FOUND + id));
        Boolean wasActive = user.getActive();
        userService.toggleActive(id);
        securityAuditService.log(AuditEventType.USER_STATUS_CHANGED,
                "User " + user.getUsername() + AUDIT_ID_SUFFIX + id + ") status changed from " +
                        (Boolean.TRUE.equals(wasActive) ? "active" : "inactive") + " to " + (Boolean.TRUE.equals(wasActive) ? "inactive" : "active"));
        redirectAttributes.addFlashAttribute(ATTR_SUCCESS_MESSAGE, "Status pengguna berhasil diubah");
        return "redirect:/users/" + id;
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
            return "redirect:/users/" + id;
        }
    }
}
