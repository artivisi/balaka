package com.artivisi.accountingfinance.entity;

import com.artivisi.accountingfinance.enums.Role;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User extends BaseEntity {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 100, message = "Username must be between 3 and 100 characters")
    @Column(name = "username", nullable = false, unique = true, length = 100)
    private String username;

    @NotBlank(message = "Password is required")
    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @NotBlank(message = "Full name is required")
    @Size(max = 255, message = "Full name must not exceed 255 characters")
    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Email(message = "Email must be valid")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "mfa_secret", length = 32)
    private String mfaSecret;

    @Column(name = "mfa_enabled", nullable = false)
    private Boolean mfaEnabled = false;

    @Column(name = "password_last_changed", nullable = false)
    private java.time.LocalDateTime passwordLastChanged = java.time.LocalDateTime.now();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private Set<UserRole> userRoles = new HashSet<>();

    /**
     * Get roles as a set of Role enums.
     */
    public Set<Role> getRoles() {
        return userRoles.stream()
                .map(UserRole::getRole)
                .collect(Collectors.toSet());
    }

    /**
     * Add a role to this user.
     */
    public void addRole(Role role) {
        UserRole userRole = new UserRole(this, role);
        userRoles.add(userRole);
    }

    /**
     * Add a role to this user with creator info.
     */
    public void addRole(Role role, String createdBy) {
        UserRole userRole = new UserRole(this, role, createdBy);
        userRoles.add(userRole);
    }

    /**
     * Remove a role from this user.
     */
    public void removeRole(Role role) {
        userRoles.removeIf(ur -> ur.getRole() == role);
    }

    /**
     * Check if user has a specific role.
     */
    public boolean hasRole(Role role) {
        return userRoles.stream().anyMatch(ur -> ur.getRole() == role);
    }

    /**
     * Set roles from a set of Role enums (replaces existing roles).
     */
    public void setRoles(Set<Role> roles, String createdBy) {
        userRoles.clear();
        for (Role role : roles) {
            addRole(role, createdBy);
        }
    }

    /**
     * Get role display names as comma-separated string.
     */
    public String getRoleDisplayNames() {
        return userRoles.stream()
                .map(ur -> ur.getRole().getDisplayName())
                .sorted()
                .collect(Collectors.joining(", "));
    }
}
