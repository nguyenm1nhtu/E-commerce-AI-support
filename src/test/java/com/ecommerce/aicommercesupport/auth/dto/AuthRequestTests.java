package com.ecommerce.aicommercesupport.auth.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class AuthRequestTests {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void acceptsValidRegistrationAndLogin() {
        assertThat(validator.validate(new RegisterRequest("customer@example.com", "password123", "Minh Tú", "Nguyễn"))).isEmpty();
        assertThat(validator.validate(new LoginRequest("customer@example.com", "password123"))).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "invalid-email"})
    void rejectsMissingOrInvalidEmail(String email) {
        assertInvalidField(new RegisterRequest(email, "password123", "Minh Tú", "Nguyễn"), "email");
        assertInvalidField(new LoginRequest(email, "password123"), "email");
    }

    @Test
    void rejectsEmailLongerThanEntityLimit() {
        var email = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(63);
        assertInvalidField(new RegisterRequest(email, "password123", "Minh Tú", "Nguyễn"), "email");
        assertInvalidField(new LoginRequest(email, "password123"), "email");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"        "})
    void rejectsMissingOrBlankPassword(String password) {
        assertInvalidField(new RegisterRequest("customer@example.com", password, "Minh Tú", "Nguyễn"), "password");
        assertInvalidField(new LoginRequest("customer@example.com", password), "password");
    }

    @Test
    void requiresEightCharactersForRegistrationButDoesNotApplyNewPasswordPolicyToLogin() {
        assertInvalidField(new RegisterRequest("customer@example.com", "1234567", "Minh Tú", "Nguyễn"), "password");
        assertThat(validator.validate(new RegisterRequest("customer@example.com", "12345678", "Minh Tú", "Nguyễn"))).isEmpty();
        assertThat(validator.validate(new LoginRequest("customer@example.com", "1234567"))).isEmpty();
    }

    @Test
    void excludesPasswordsFromToString() {
        assertThat(new RegisterRequest("customer@example.com", "secret-password", "Minh Tú", "Nguyễn").toString())
                .doesNotContain("secret-password");
        assertThat(new LoginRequest("customer@example.com", "secret-password").toString())
                .doesNotContain("secret-password");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void requiresBothNamesForRegistration(String name) {
        assertInvalidField(new RegisterRequest("customer@example.com", "password123", name, "Nguyễn"), "firstName");
        assertInvalidField(new RegisterRequest("customer@example.com", "password123", "Minh Tú", name), "lastName");
    }

    @Test
    void limitsEachNameToOneHundredCharacters() {
        var name = "a".repeat(100);
        assertThat(validator.validate(new RegisterRequest("customer@example.com", "password123", name, name)))
                .isEmpty();
        assertInvalidField(new RegisterRequest("customer@example.com", "password123", name + "a", name), "firstName");
        assertInvalidField(new RegisterRequest("customer@example.com", "password123", name, name + "a"), "lastName");
    }

    private void assertInvalidField(Object request, String field) {
        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains(field);
    }
}
