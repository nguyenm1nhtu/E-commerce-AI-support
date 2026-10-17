package com.ecommerce.aicommercesupport.order.controller;

import com.ecommerce.aicommercesupport.order.service.CommerceFixtureService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class CommerceFixtureProfileTests {

    @ParameterizedTest
    @ValueSource(strings = {"default", "prod", "test,prod", "dev,prod"})
    void fixtureBeansAreUnavailableOutsideDevelopmentAndWhenProductionIsActive(String profiles) {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles(profiles.split(","));
            context.register(OrderFixtureController.class, CommerceFixtureService.class);
            context.refresh();
            assertThat(context.getBeansOfType(OrderFixtureController.class)).isEmpty();
            assertThat(context.getBeansOfType(CommerceFixtureService.class)).isEmpty();
        }
    }
}
