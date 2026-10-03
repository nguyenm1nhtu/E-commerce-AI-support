package com.ecommerce.aicommercesupport.user.entity;

import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserPersistenceTests {

    private static final String PASSWORD_HASH = "{bcrypt}$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Validator validator;

    @Test
    void persistsGeneratedUuidEmailPasswordHashAndEachRole() {
        for (var role : UserRole.values()) {
            var user = new User(role.name() + "@example.com", PASSWORD_HASH, role, "Minh Tú", "Nguyễn");
            entityManager.persist(user);
            entityManager.flush();
            entityManager.clear();

            assertThat(user.getId()).isNotNull();
            var loaded = entityManager.find(User.class, user.getId());
            assertThat(loaded.getEmail()).isEqualTo(user.getEmail());
            assertThat(loaded.getFirstName()).isEqualTo("Minh Tú");
            assertThat(loaded.getLastName()).isEqualTo("Nguyễn");
            assertThat(loaded.getPasswordHash()).isEqualTo(PASSWORD_HASH);
            assertThat(loaded.getRole()).isEqualTo(role);
            assertThat(jdbc.queryForObject("SELECT role FROM users WHERE id = ?", String.class, user.getId()))
                    .isEqualTo(role.name());
        }
    }

    @Test
    void databaseRejectsDuplicateEmail() {
        insertUser("customer@example.com", PASSWORD_HASH, "CUSTOMER");

        assertThatThrownBy(() -> insertUser("customer@example.com", PASSWORD_HASH, "SUPPORT_AGENT"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsMissingValuesBlankCredentialsAndUnsupportedRole() {
        assertThatThrownBy(() -> insertUser(null, PASSWORD_HASH, "CUSTOMER"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertUser(" ", PASSWORD_HASH, "CUSTOMER"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertUser("customer@example.com", null, "CUSTOMER"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertUser("customer@example.com", " ", "CUSTOMER"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertUser("customer@example.com", PASSWORD_HASH, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertUser("customer@example.com", PASSWORD_HASH, "UNKNOWN"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void validatesEmailPasswordHashAndRoleBeforePersistence() {
        assertThat(validator.validate(new User("customer@example.com", PASSWORD_HASH, UserRole.CUSTOMER, "Minh Tú", "Nguyễn")))
                .isEmpty();
        assertThat(validator.validate(new User("invalid-email", " ", null, "Minh Tú", "Nguyễn")))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("email", "passwordHash", "role");
        assertThat(validator.validate(new User(" ", PASSWORD_HASH, UserRole.CUSTOMER, "Minh Tú", "Nguyễn")))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("email");
        assertThat(validator.validate(new User("a".repeat(250) + "@example.com", "x".repeat(256), UserRole.CUSTOMER, "Minh Tú", "Nguyễn")))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("email", "passwordHash");
    }

    private void insertUser(String email, String passwordHash, String role) {
        jdbc.update("INSERT INTO users (id, email, password_hash, role, first_name, last_name) VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), email, passwordHash, role, "Minh Tú", "Nguyễn");
    }

    @Test
    void validatesRequiredNamesAndLength() {
        for (var name : new String[] {null, "", " ", "a".repeat(101)}) {
            assertThat(validator.validate(new User("customer@example.com", PASSWORD_HASH, UserRole.CUSTOMER, name, name)))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .contains("firstName", "lastName");
        }
    }

    @Test
    void databaseRejectsBlankAndOverlongNames() {
        insertUser("customer@example.com", PASSWORD_HASH, "CUSTOMER");
        for (var column : new String[] {"first_name", "last_name"}) {
            for (var name : new String[] {" ", "a".repeat(101)}) {
                assertThatThrownBy(() -> jdbc.update("UPDATE users SET " + column + " = ?", name))
                        .isInstanceOf(DataIntegrityViolationException.class);
            }
        }
    }
}
