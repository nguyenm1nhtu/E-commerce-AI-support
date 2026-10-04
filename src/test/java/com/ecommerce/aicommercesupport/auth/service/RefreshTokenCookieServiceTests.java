package com.ecommerce.aicommercesupport.auth.service;

import java.time.Duration;

import com.ecommerce.aicommercesupport.auth.config.RefreshTokenCookieProperties;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class RefreshTokenCookieServiceTests {

    private final RefreshTokenCookieService service = new RefreshTokenCookieService(
            new RefreshTokenCookieProperties("refresh_token", true, "Lax", Duration.ofDays(7)));

    @Test
    void writesHostOnlyHttpOnlyCookieAndPreservesOtherCookies() {
        var response = new MockHttpServletResponse();
        response.addHeader(HttpHeaders.SET_COOKIE, "existing=value; Path=/");

        service.write(response, "opaque-token");

        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).hasSize(2)
                .contains("existing=value; Path=/");
        var header = response.getHeaders(HttpHeaders.SET_COOKIE).get(1);
        assertThat(header).contains("refresh_token=opaque-token", "HttpOnly", "Secure",
                "SameSite=Lax", "Path=/api/auth", "Max-Age=604800").doesNotContain("Domain=");
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    @Test
    void limitsCookieLifetimeToTokenLifetimeAndConfiguredMaximum() {
        var shorter = new MockHttpServletResponse();
        service.write(shorter, "token", Duration.ofMinutes(5));
        assertThat(shorter.getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=300;");

        var longer = new MockHttpServletResponse();
        service.write(longer, "token", Duration.ofDays(30));
        assertThat(longer.getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=604800;");
    }

    @Test
    void rejectsBlankTokensExpiredTokensAndHeaderInjection() {
        var response = new MockHttpServletResponse();
        assertThatIllegalArgumentException().isThrownBy(() -> service.write(response, " "));
        assertThatIllegalArgumentException().isThrownBy(() -> service.write(response, null));
        assertThatIllegalArgumentException().isThrownBy(() -> service.write(response, "token", Duration.ZERO));
        assertThatIllegalArgumentException().isThrownBy(() -> service.write(response, "token", Duration.ofSeconds(-1)));
        assertThatIllegalArgumentException().isThrownBy(() -> service.write(response, "token\r\nX-Injected: true"));
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    }

    @Test
    void readsOnlyConfiguredCookieAndRejectsMissingBlankOrAmbiguousCookies() {
        var request = new MockHttpServletRequest();
        request.addParameter("refreshToken", "query-token");
        request.setContent("{\"refreshToken\":\"body-token\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(service.read(request)).isEmpty();
        request.setCookies(new Cookie("other", "value"));
        assertThat(service.read(request)).isEmpty();
        request.setCookies(new Cookie("refresh_token", " "));
        assertThat(service.read(request)).isEmpty();
        request.setCookies(new Cookie("other", "value"), new Cookie("refresh_token", "opaque-token"));
        assertThat(service.read(request)).contains("opaque-token");
        request.setCookies(new Cookie("refresh_token", "first"), new Cookie("refresh_token", "second"));
        assertThat(service.read(request)).isEmpty();
    }

    @Test
    void clearsCookieUsingSameNamePathAndSecurityAttributes() {
        var response = new MockHttpServletResponse();
        service.clear(response);
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).startsWith("refresh_token=;")
                .contains("Max-Age=0;", "Path=/api/auth", "HttpOnly", "Secure", "SameSite=Lax")
                .doesNotContain("Domain=");
    }

    @Test
    void supportsCustomNameAndLocalHttpConfiguration() {
        var local = new RefreshTokenCookieService(
                new RefreshTokenCookieProperties("custom_refresh", false, "Strict", Duration.ofHours(1)));
        var response = new MockHttpServletResponse();
        local.write(response, "token");
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).startsWith("custom_refresh=token;")
                .contains("HttpOnly", "SameSite=Strict", "Max-Age=3600;").doesNotContain("Secure");
        var request = new MockHttpServletRequest();
        request.setCookies(new Cookie("custom_refresh", "token"));
        assertThat(local.read(request)).contains("token");
        local.clear(response);
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE).get(1)).startsWith("custom_refresh=;")
                .contains("Max-Age=0;").doesNotContain("Secure");
    }
}
