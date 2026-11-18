package com.artivisi.accountingfinance.service;

import com.artivisi.accountingfinance.TestcontainersConfiguration;
import com.artivisi.accountingfinance.entity.User;
import com.artivisi.accountingfinance.enums.Role;
import com.artivisi.accountingfinance.repository.DeviceTokenRepository;
import com.artivisi.accountingfinance.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for UserService.
 * Tests actual database queries and business logic.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("UserService Integration Tests")
@WithMockUser(username = "testadmin", roles = {"ADMIN"})
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PasswordChangeService passwordChangeService;

    @Autowired
    private DeviceAuthService deviceAuthService;

    @Autowired
    private DeviceTokenRepository deviceTokenRepository;

    @Nested
    @DisplayName("Find Operations")
    class FindOperationsTests {

        @Test
        @DisplayName("findAll should return paginated results")
        void findAllShouldReturnPaginatedResults() {
            createTestUser();
            createTestUser();

            Page<User> page = userService.findAll(PageRequest.of(0, 10));

            assertThat(page.getContent()).isNotEmpty();
            assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("findById should return user")
        void findByIdShouldReturnUser() {
            User user = createTestUser();

            Optional<User> found = userService.findById(user.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getUsername()).isEqualTo(user.getUsername());
        }

        @Test
        @DisplayName("findById should return empty for invalid ID")
        void findByIdShouldReturnEmptyForInvalidId() {
            UUID invalidId = UUID.randomUUID();

            Optional<User> found = userService.findById(invalidId);

            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("findByUsername should return user")
        void findByUsernameShouldReturnUser() {
            User user = createTestUser();

            Optional<User> found = userService.findByUsername(user.getUsername());

            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(user.getId());
        }

        @Test
        @DisplayName("findByUsername should return empty for invalid username")
        void findByUsernameShouldReturnEmptyForInvalidUsername() {
            Optional<User> found = userService.findByUsername("nonexistent");

            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("search should find by username")
        void searchShouldFindByUsername() {
            User user = buildTestUser();
            user.setUsername("uniqueuser123");
            userService.create(user, Set.of(Role.STAFF));

            Page<User> page = userService.search("unique", PageRequest.of(0, 10));

            assertThat(page.getContent()).isNotEmpty();
            assertThat(page.getContent()).anyMatch(u -> u.getUsername().contains("unique"));
        }

        @Test
        @DisplayName("search should find by full name")
        void searchShouldFindByFullName() {
            User user = buildTestUser();
            user.setFullName("Bambang Unique Name");
            userService.create(user, Set.of(Role.STAFF));

            Page<User> page = userService.search("Bambang Unique", PageRequest.of(0, 10));

            assertThat(page.getContent()).isNotEmpty();
            assertThat(page.getContent()).anyMatch(u -> u.getFullName().contains("Bambang"));
        }

        @Test
        @DisplayName("search should find by email")
        void searchShouldFindByEmail() {
            User user = buildTestUser();
            user.setEmail("unique.search@test.com");
            userService.create(user, Set.of(Role.STAFF));

            Page<User> page = userService.search("unique.search", PageRequest.of(0, 10));

            assertThat(page.getContent()).isNotEmpty();
            assertThat(page.getContent()).anyMatch(u ->
                u.getEmail() != null && u.getEmail().contains("unique.search"));
        }

        @Test
        @DisplayName("search should return all when search is blank")
        void searchShouldReturnAllWhenSearchIsBlank() {
            createTestUser();
            createTestUser();

            Page<User> page = userService.search("", PageRequest.of(0, 10));

            assertThat(page.getContent()).isNotEmpty();
        }

        @Test
        @DisplayName("search should return all when search is null")
        void searchShouldReturnAllWhenSearchIsNull() {
            createTestUser();

            Page<User> page = userService.search(null, PageRequest.of(0, 10));

            assertThat(page.getContent()).isNotEmpty();
        }

        @Test
        @DisplayName("findAllActive should return only active users")
        void findAllActiveShouldReturnOnlyActiveUsers() {
            User active = createTestUser();
            User inactive = createTestUser();
            userService.toggleActive(inactive.getId());

            List<User> activeUsers = userService.findAllActive();

            assertThat(activeUsers)
                    .allMatch(User::getActive)
                    .anyMatch(u -> u.getId().equals(active.getId()))
                    .noneMatch(u -> u.getId().equals(inactive.getId()));
        }
    }

    @Nested
    @DisplayName("Create User")
    class CreateUserTests {

        @Test
        @DisplayName("create should save user with encoded password")
        void createShouldSaveWithEncodedPassword() {
            User user = buildTestUser();
            String rawPassword = user.getPassword();

            User saved = userService.create(user, Set.of(Role.STAFF));

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getPassword()).isNotEqualTo(rawPassword);
            assertThat(passwordEncoder.matches(rawPassword, saved.getPassword())).isTrue();
            assertThat(saved.getPasswordChangeRequired()).as("administrator-chosen password").isTrue();
            assertThat(saved.getCredentialsChangedAt()).isNotNull();
        }

        @Test
        @DisplayName("create should reject an email that differs from an existing one only in case")
        void createShouldThrowForDuplicateEmailInAnotherCase() {
            User first = buildTestUser();
            first.setEmail("Kasir.Satu@Test.com");
            userService.create(first, Set.of(Role.STAFF));

            User second = buildTestUser();
            second.setEmail("  kasir.satu@test.COM ");

            assertThatThrownBy(() -> userService.create(second, Set.of(Role.STAFF)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email already exists");
        }

        @Test
        @DisplayName("create should assign roles")
        void createShouldAssignRoles() {
            User user = buildTestUser();
            Set<Role> roles = Set.of(Role.ACCOUNTANT, Role.STAFF);

            User saved = userService.create(user, roles);

            assertThat(saved.getRoles()).containsExactlyInAnyOrderElementsOf(roles);
            assertThat(saved.hasRole(Role.ACCOUNTANT)).isTrue();
            assertThat(saved.hasRole(Role.STAFF)).isTrue();
        }

        @Test
        @DisplayName("create should throw for duplicate username")
        void createShouldThrowForDuplicateUsername() {
            User first = createTestUser();

            User second = buildTestUser();
            second.setUsername(first.getUsername());

            assertThatThrownBy(() -> userService.create(second, Set.of(Role.STAFF)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username already exists");
        }

        @Test
        @DisplayName("create should throw for duplicate email")
        void createShouldThrowForDuplicateEmail() {
            User first = buildTestUser();
            first.setEmail("duplicate@test.com");
            userService.create(first, Set.of(Role.STAFF));

            User second = buildTestUser();
            second.setEmail("duplicate@test.com");

            assertThatThrownBy(() -> userService.create(second, Set.of(Role.STAFF)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email already exists");
        }

        @Test
        @DisplayName("create should allow null email")
        void createShouldAllowNullEmail() {
            User user = buildTestUser();
            user.setEmail(null);

            User saved = userService.create(user, Set.of(Role.STAFF));

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getEmail()).isNull();
        }

        @Test
        @DisplayName("create should allow blank email")
        void createShouldAllowBlankEmail() {
            User user = buildTestUser();
            user.setEmail("");

            User saved = userService.create(user, Set.of(Role.STAFF));

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getEmail()).as("blank stored as no address").isNull();
        }
    }

    @Nested
    @DisplayName("Update User")
    class UpdateUserTests {

        @Test
        @DisplayName("update should modify user fields")
        void updateShouldModifyFields() {
            User user = createTestUser();

            User updateData = buildUpdateData(user);
            updateData.setFullName("Updated Full Name");
            updateData.setEmail("updated@test.com");

            User updated = userService.update(user.getId(), updateData, Set.of(Role.STAFF));

            assertThat(updated.getFullName()).isEqualTo("Updated Full Name");
            assertThat(updated.getEmail()).isEqualTo("updated@test.com");
        }

        @Test
        @DisplayName("update should allow changing username if not duplicate")
        void updateShouldAllowChangingUsername() {
            User user = createTestUser();
            String newUsername = "newuser" + System.nanoTime();

            User updateData = buildUpdateData(user);
            updateData.setUsername(newUsername);

            User updated = userService.update(user.getId(), updateData, Set.of(Role.STAFF));

            assertThat(updated.getUsername()).isEqualTo(newUsername);
        }

        @Test
        @DisplayName("update should throw for duplicate username")
        void updateShouldThrowForDuplicateUsername() {
            User first = createTestUser();
            User second = createTestUser();

            User updateData = buildUpdateData(second);
            updateData.setUsername(first.getUsername());

            assertThatThrownBy(() -> userService.update(second.getId(), updateData, Set.of(Role.STAFF)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username already exists");
        }

        @Test
        @DisplayName("update should throw for duplicate email")
        void updateShouldThrowForDuplicateEmail() {
            User first = buildTestUser();
            first.setEmail("existing@test.com");
            userService.create(first, Set.of(Role.STAFF));

            User second = createTestUser();

            User updateData = buildUpdateData(second);
            updateData.setEmail("existing@test.com");

            assertThatThrownBy(() -> userService.update(second.getId(), updateData, Set.of(Role.STAFF)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email already exists");
        }

        @Test
        @DisplayName("update should allow keeping same email")
        void updateShouldAllowKeepingSameEmail() {
            User user = buildTestUser();
            user.setEmail("same@test.com");
            user = userService.create(user, Set.of(Role.STAFF));

            User updateData = buildUpdateData(user);
            updateData.setEmail("same@test.com");
            updateData.setFullName("Updated Name");

            User updated = userService.update(user.getId(), updateData, Set.of(Role.STAFF));

            assertThat(updated.getFullName()).isEqualTo("Updated Name");
            assertThat(updated.getEmail()).isEqualTo("same@test.com");
        }

        @Test
        @DisplayName("update should update roles")
        void updateShouldUpdateRoles() {
            User user = createTestUser();

            User updateData = buildUpdateData(user);
            Set<Role> newRoles = Set.of(Role.ADMIN, Role.OWNER);

            User updated = userService.update(user.getId(), updateData, newRoles);

            assertThat(updated.getRoles()).containsExactlyInAnyOrderElementsOf(newRoles);
        }

        @Test
        @DisplayName("update should throw for non-existent user")
        void updateShouldThrowForNonExistentUser() {
            UUID invalidId = UUID.randomUUID();
            User updateData = buildTestUser();

            assertThatThrownBy(() -> userService.update(invalidId, updateData, Set.of(Role.STAFF)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");
        }

        @Test
        @DisplayName("update should allow null email without duplicate check")
        void updateShouldAllowNullEmail() {
            User user = createTestUser();

            User updateData = buildUpdateData(user);
            updateData.setEmail(null);
            updateData.setFullName("Updated With Null Email");

            User updated = userService.update(user.getId(), updateData, Set.of(Role.STAFF));

            assertThat(updated.getFullName()).isEqualTo("Updated With Null Email");
            assertThat(updated.getEmail()).isNull();
        }

        @Test
        @DisplayName("update should allow blank email without duplicate check")
        void updateShouldAllowBlankEmail() {
            User user = createTestUser();

            User updateData = buildUpdateData(user);
            updateData.setEmail("");
            updateData.setFullName("Updated With Blank Email");

            User updated = userService.update(user.getId(), updateData, Set.of(Role.STAFF));

            assertThat(updated.getFullName()).isEqualTo("Updated With Blank Email");
        }
    }

    @Nested
    @DisplayName("Password Operations")
    class PasswordOperationsTests {

        private static final String STRONG = "Kuat#Sekali2026";

        @Test
        @DisplayName("Administrator-set password is encoded, flags a forced change, stamps the change and revokes device tokens")
        void setByAdministratorFlagsAndRevokes() {
            User user = userRepository.saveAndFlush(userWithPassword("Lama#Sekali2025", false));
            deviceAuthService.createAccessToken(user, "test-cli", "Laptop");
            assertThat(deviceTokenRepository.findActiveByUser(user)).hasSize(1);

            passwordChangeService.setByAdministrator(user.getId(), STRONG, STRONG, "testadmin");

            User updated = userRepository.findById(user.getId()).orElseThrow();
            assertThat(passwordEncoder.matches(STRONG, updated.getPassword())).isTrue();
            assertThat(updated.getPasswordChangeRequired()).isTrue();
            assertThat(updated.getCredentialsChangedAt()).isNotNull();
            assertThat(deviceTokenRepository.findActiveByUser(user)).isEmpty();
        }

        @Test
        @DisplayName("Administrator path enforces the policy and the confirmation, changing nothing on refusal")
        void setByAdministratorEnforcesPolicy() {
            User user = userRepository.saveAndFlush(userWithPassword("Lama#Sekali2025", false));
            String before = user.getPassword();

            assertThatThrownBy(() -> passwordChangeService.setByAdministrator(user.getId(), STRONG, STRONG + "x", "testadmin"))
                .isInstanceOf(PasswordChangeException.class)
                .hasMessage("Password tidak cocok");
            assertThatThrownBy(() -> passwordChangeService.setByAdministrator(user.getId(), "pendek", "pendek", "testadmin"))
                .isInstanceOf(PasswordChangeException.class)
                .hasMessageContaining("Password minimal 12 karakter");

            User unchanged = userRepository.findById(user.getId()).orElseThrow();
            assertThat(unchanged.getPassword()).isEqualTo(before);
            assertThat(unchanged.getPasswordChangeRequired()).isFalse();
        }

        @Test
        @DisplayName("An administrator cannot set their own password through the administrator path")
        void setByAdministratorRefusesOwnAccount() {
            User self = userRepository.saveAndFlush(userWithPassword("Lama#Sekali2025", false));

            assertThatThrownBy(() -> passwordChangeService.setByAdministrator(self.getId(), STRONG, STRONG, self.getUsername()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("/account/password");
        }

        @Test
        @DisplayName("Administrator path throws for a non-existent user")
        void setByAdministratorThrowsForNonExistentUser() {
            UUID invalidId = UUID.randomUUID();

            assertThatThrownBy(() -> passwordChangeService.setByAdministrator(invalidId, STRONG, STRONG, "testadmin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");
        }

        @Test
        @DisplayName("Own change requires the current password, refuses reuse, and clears the forced-change flag")
        void changeOwnPassword() {
            User user = userRepository.saveAndFlush(userWithPassword("Sementara#2025", true));

            assertThatThrownBy(() -> passwordChangeService.changeOwnPassword(user.getUsername(), "salah", STRONG, STRONG))
                .isInstanceOf(PasswordChangeException.class)
                .hasMessage("Password saat ini salah");
            assertThatThrownBy(() -> passwordChangeService.changeOwnPassword(
                    user.getUsername(), "Sementara#2025", "Sementara#2025", "Sementara#2025"))
                .isInstanceOf(PasswordChangeException.class)
                .hasMessage("Password baru harus berbeda dari password saat ini");
            assertThat(userRepository.findById(user.getId()).orElseThrow().getPasswordChangeRequired()).isTrue();

            var changedAt = passwordChangeService.changeOwnPassword(user.getUsername(), "Sementara#2025", STRONG, STRONG);

            User updated = userRepository.findById(user.getId()).orElseThrow();
            assertThat(passwordEncoder.matches(STRONG, updated.getPassword())).isTrue();
            assertThat(updated.getPasswordChangeRequired()).isFalse();
            assertThat(updated.getCredentialsChangedAt()).isEqualTo(changedAt);
        }

        private User userWithPassword(String raw, boolean changeRequired) {
            User user = buildTestUser();
            user.setPassword(passwordEncoder.encode(raw));
            user.setPasswordChangeRequired(changeRequired);
            user.setRoles(Set.of(Role.STAFF), "testadmin");
            return user;
        }
    }

    @Nested
    @DisplayName("Toggle Active")
    class ToggleActiveTests {

        @Test
        @DisplayName("toggleActive should deactivate active user")
        void toggleActiveShouldDeactivateActiveUser() {
            User user = createTestUser();
            assertThat(user.getActive()).isTrue();

            userService.toggleActive(user.getId());

            User updated = userRepository.findById(user.getId()).orElseThrow();
            assertThat(updated.getActive()).isFalse();
        }

        @Test
        @DisplayName("toggleActive should activate inactive user")
        void toggleActiveShouldActivateInactiveUser() {
            User user = createTestUser();
            userService.toggleActive(user.getId()); // Deactivate

            userService.toggleActive(user.getId()); // Activate again

            User updated = userRepository.findById(user.getId()).orElseThrow();
            assertThat(updated.getActive()).isTrue();
        }

        @Test
        @DisplayName("toggleActive should throw for non-existent user")
        void toggleActiveShouldThrowForNonExistentUser() {
            UUID invalidId = UUID.randomUUID();

            assertThatThrownBy(() -> userService.toggleActive(invalidId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");
        }
    }

    @Nested
    @DisplayName("Delete User")
    class DeleteUserTests {

        @Test
        @DisplayName("delete should remove user")
        void deleteShouldRemoveUser() {
            User user = createTestUser();
            UUID userId = user.getId();

            userService.delete(userId);

            assertThat(userRepository.findById(userId)).isEmpty();
        }

        @Test
        @DisplayName("delete should throw for non-existent user")
        void deleteShouldThrowForNonExistentUser() {
            UUID invalidId = UUID.randomUUID();

            assertThatThrownBy(() -> userService.delete(invalidId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");
        }

        @Test
        @WithMockUser(username = "selfuser", roles = {"ADMIN"})
        @DisplayName("delete should throw when deleting self")
        void deleteShouldThrowWhenDeletingSelf() {
            User user = buildTestUser();
            user.setUsername("selfuser");
            user = userService.create(user, Set.of(Role.ADMIN));
            UUID userId = user.getId();

            assertThatThrownBy(() -> userService.delete(userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot delete your own account");
        }
    }

    @Nested
    @DisplayName("Count Operations")
    class CountOperationsTests {

        @Test
        @DisplayName("count should return total user count")
        void countShouldReturnTotalCount() {
            long initialCount = userService.count();

            createTestUser();
            createTestUser();

            long newCount = userService.count();

            assertThat(newCount).isEqualTo(initialCount + 2);
        }
    }

    // Helper methods

    private User createTestUser() {
        User user = buildTestUser();
        return userService.create(user, Set.of(Role.STAFF));
    }

    private User buildTestUser() {
        User user = new User();
        user.setUsername("user" + System.nanoTime());
        user.setPassword("password123");
        user.setFullName("Test User " + System.currentTimeMillis());
        user.setActive(true);
        return user;
    }

    private User buildUpdateData(User existing) {
        User updateData = new User();
        updateData.setUsername(existing.getUsername());
        updateData.setFullName(existing.getFullName());
        updateData.setEmail(existing.getEmail());
        updateData.setActive(existing.getActive());
        return updateData;
    }
}
