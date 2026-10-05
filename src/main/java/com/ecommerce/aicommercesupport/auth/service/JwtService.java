package com.ecommerce.aicommercesupport.auth.service;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import com.ecommerce.aicommercesupport.auth.config.JwtProperties;
import com.ecommerce.aicommercesupport.user.entity.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Clock jwtClock;

    public Jwt generateAccessToken(UUID userId, UserRole role) {
        Assert.notNull(userId, "User ID is required");
        Assert.notNull(role, "User role is required");
        var now = jwtClock.instant().truncatedTo(ChronoUnit.SECONDS);
        var claims = JwtClaimsSet.builder()
                .issuer(properties.issuer()).audience(List.of(properties.audience()))
                .subject(userId.toString()).id(UUID.randomUUID().toString())
                .issuedAt(now).notBefore(now).expiresAt(now.plus(properties.accessTokenTtl()))
                .claim("role", role.name()).claim("token_use", "access").build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims));
    }
}
