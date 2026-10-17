package com.ecommerce.aicommercesupport.order.controller;

import com.ecommerce.aicommercesupport.order.service.CommerceFixtureService;
import com.ecommerce.aicommercesupport.payment.controller.PaymentFixtureController;
import com.ecommerce.aicommercesupport.shipment.controller.ShipmentFixtureController;
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
            context.register(OrderFixtureController.class, OrderItemFixtureController.class,
                    PaymentFixtureController.class, ShipmentFixtureController.class, CommerceFixtureService.class);
            context.refresh();
            assertThat(context.getBeansOfType(OrderFixtureController.class)).isEmpty();
            assertThat(context.getBeansOfType(OrderItemFixtureController.class)).isEmpty();
            assertThat(context.getBeansOfType(PaymentFixtureController.class)).isEmpty();
            assertThat(context.getBeansOfType(ShipmentFixtureController.class)).isEmpty();
            assertThat(context.getBeansOfType(CommerceFixtureService.class)).isEmpty();
        }
    }
}
