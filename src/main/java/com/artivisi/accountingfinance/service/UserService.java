package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.enums.Role;
import com.artivisi.accountingfinance.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private static final String USER_NOT_FOUND = "User not found: ";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public Page<User> findAll(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<User> search(String search, Pageable pageable) {
        if (search == null || search.isBlank()) {
            return userRepository.findAll(pageable);
        }
        return userRepository.findByUsernameContainingIgnoreCaseOrFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                search, search, search, pageable);
    }

    @Transactional(readOnly = true)
    public Optional<User> findById(UUID id) {
        return userRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Transactional
    public User create(User user, Set<Role> roles) {
        // Validate unique constraints
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new IllegalArgumentException("Username already exists: " + user.getUsername());
        }
        if (user.getEmail() != null && !user.getEmail().isBlank() && userRepository.existsByEmail(user.getEmail())) {
            throw new IllegalArgumentException("Email already exists: " + user.getEmail());
        }

        // Encode password
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        // Set roles
        String currentUser = getCurrentUsername();
        user.setRoles(roles, currentUser);

        User savedUser = userRepository.save(user);
        log.info("Created user: {} with roles: {}", savedUser.getUsername(), roles);
        return savedUser;
    }

    @Transactional
    public User update(UUID id, User updated, Set<Role> roles) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(USER_NOT_FOUND + id));

        // Check username uniqueness if changed
        if (!user.getUsername().equals(updated.getUsername()) &&
            userRepository.existsByUsername(updated.getUsername())) {
            throw new IllegalArgumentException("Username already exists: " + updated.getUsername());
        }

        // Check email uniqueness if changed
        if (updated.getEmail() != null && !updated.getEmail().isBlank() &&
            !updated.getEmail().equals(user.getEmail()) &&
            userRepository.existsByEmail(updated.getEmail())) {
            throw new IllegalArgumentException("Email already exists: " + updated.getEmail());
        }

        // Update fields
        user.setUsername(updated.getUsername());
        user.setFullName(updated.getFullName());
        user.setEmail(updated.getEmail());
        user.setActive(updated.getActive());

        // Update roles
        String currentUser = getCurrentUsername();
        user.setRoles(roles, currentUser);

        User savedUser = userRepository.save(user);
        log.info("Updated user: {} with roles: {}", savedUser.getUsername(), roles);
        return savedUser;
    }

    @Transactional
    public void changePassword(UUID id, String newPassword) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(USER_NOT_FOUND + id));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("Changed password for user: {}", user.getUsername());
    }

    @Transactional
    public void toggleActive(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(USER_NOT_FOUND + id));

        user.setActive(!user.getActive());
        userRepository.save(user);
        log.info("Toggled active status for user: {} to {}", user.getUsername(), user.getActive());
    }

    @Transactional
    public void delete(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(USER_NOT_FOUND + id));

        // Prevent self-deletion
        String currentUser = getCurrentUsername();
        if (user.getUsername().equals(currentUser)) {
            throw new IllegalArgumentException("Cannot delete your own account");
        }

        userRepository.delete(user);
        log.info("Deleted user: {}", user.getUsername());
    }

    @Transactional(readOnly = true)
    public List<User> findAllActive() {
        return userRepository.findByActiveTrue();
    }

    @Transactional(readOnly = true)
    public long count() {
        return userRepository.count();
    }

    private String getCurrentUsername() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
