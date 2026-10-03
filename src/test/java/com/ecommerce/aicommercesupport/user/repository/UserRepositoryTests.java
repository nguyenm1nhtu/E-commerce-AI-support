package com.ecommerce.aicommercesupport.user.repository;

import com.ecommerce.aicommercesupport.user.entity.User;
import com.ecommerce.aicommercesupport.user.entity.UserRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserRepositoryTests {

    @Autowired
    private UserRepository users;

    @Autowired
    private EntityManager entityManager;

    @Test
    void findsUserByEmailAndReturnsEmptyWhenAbsent() {
        var customer = users.saveAndFlush(new User("customer@example.com", "encoded-password", UserRole.CUSTOMER));
        var support = users.saveAndFlush(new User("support@example.com", "encoded-password", UserRole.SUPPORT_AGENT));
        entityManager.clear();

        assertThat(users.findByEmail("customer@example.com")).map(User::getId).contains(customer.getId());
        assertThat(users.findByEmail("support@example.com")).map(User::getId).contains(support.getId());
        assertThat(users.findByEmail("missing@example.com")).isEmpty();
    }

    @Test
    void checksWhetherEmailAlreadyExists() {
        users.saveAndFlush(new User("customer@example.com", "encoded-password", UserRole.CUSTOMER));
        entityManager.clear();

        assertThat(users.existsByEmail("customer@example.com")).isTrue();
        assertThat(users.existsByEmail("missing@example.com")).isFalse();
    }
}
