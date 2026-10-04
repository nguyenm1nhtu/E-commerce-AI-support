package com.ecommerce.aicommercesupport.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RefreshTokenCookieProperties.class)
public class AuthCookieConfiguration {
}
