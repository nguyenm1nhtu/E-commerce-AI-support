package com.ecommerce.aicommercesupport.auth.config;

import java.time.Duration;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class JwtConfigurationTests {

    private static final String SECRET = Base64.getEncoder().encodeToString(new byte[32]);
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(JwtConfiguration.class);

    @Test
    void registersEncoderDecoderAndConfigurableLifetimeWithoutLoggingSecret() {
        runner.withPropertyValues("app.auth.jwt.secret=" + SECRET, "app.auth.jwt.access-token-ttl=5m")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(JwtEncoder.class).hasSingleBean(JwtDecoder.class);
                    var properties = context.getBean(JwtProperties.class);
                    assertThat(properties.accessTokenTtl()).isEqualTo(Duration.ofMinutes(5));
                    assertThat(properties.toString()).doesNotContain(SECRET).contains("[REDACTED]");
                });
    }

    @Test
    void refusesStartupWithoutSecret() {
        runner.run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not-base64!", "c2hvcnQ="})
    void rejectsMissingMalformedAndShortSecrets(String secret) {
        runner.withPropertyValues("app.auth.jwt.secret=" + secret).run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"issuer=", "audience=", "access-token-ttl=0s", "access-token-ttl=-1s", "access-token-ttl=1500ms"})
    void rejectsInvalidMetadataOrLifetime(String property) {
        runner.withPropertyValues("app.auth.jwt.secret=" + SECRET, "app.auth.jwt." + property)
                .run(context -> assertThat(context).hasFailed());
    }
}
