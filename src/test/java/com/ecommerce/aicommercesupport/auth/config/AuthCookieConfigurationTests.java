package com.ecommerce.aicommercesupport.auth.config;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AuthCookieConfigurationTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AuthCookieConfiguration.class);

    @Test
    void bindsSafeDefaultsWithoutExternalConfiguration() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var properties = context.getBean(RefreshTokenCookieProperties.class);
            assertThat(properties.name()).isEqualTo("refresh_token");
            assertThat(properties.secure()).isTrue();
            assertThat(properties.sameSite()).isEqualTo("Lax");
            assertThat(properties.maxAge()).isEqualTo(Duration.ofDays(7));
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"default", "dev", "prod"})
    void loadsCookieSecurityFromApplicationProfiles(String profile) {
        runner.withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=" + profile)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var properties = context.getBean(RefreshTokenCookieProperties.class);
                    assertThat(properties.secure()).isEqualTo(!"dev".equals(profile));
                });
    }

    @Test
    void acceptsSecureCrossSiteCookiesAndCustomLifetime() {
        runner.withPropertyValues("app.auth.refresh-token-cookie.same-site=None",
                        "app.auth.refresh-token-cookie.max-age=2d")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var properties = context.getBean(RefreshTokenCookieProperties.class);
                    assertThat(properties.sameSite()).isEqualTo("None");
                    assertThat(properties.maxAge()).isEqualTo(Duration.ofDays(2));
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"max-age=0s", "max-age=-1s", "max-age=500ms", "max-age=1500ms",
            "same-site=invalid", "name=", "name=invalid;name"})
    void rejectsInvalidConfiguration(String property) {
        runner.withPropertyValues("app.auth.refresh-token-cookie." + property)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsCrossSiteCookiesWithoutSecure() {
        runner.withPropertyValues("app.auth.refresh-token-cookie.same-site=None",
                        "app.auth.refresh-token-cookie.secure=false")
                .run(context -> assertThat(context).hasFailed());
    }
}
