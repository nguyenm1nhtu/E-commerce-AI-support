package com.ecommerce.aicommercesupport.order.controller;

import com.ecommerce.aicommercesupport.common.cache.CacheEviction;
import com.ecommerce.aicommercesupport.common.security.CurrentUserId;
import com.ecommerce.aicommercesupport.order.repository.OrderRepository;
import com.ecommerce.aicommercesupport.order.repository.OrderItemRepository;
import com.ecommerce.aicommercesupport.order.service.OrderService;
import com.ecommerce.aicommercesupport.order.service.OrderItemService;
import com.ecommerce.aicommercesupport.payment.controller.PaymentController;
import com.ecommerce.aicommercesupport.payment.repository.PaymentRepository;
import com.ecommerce.aicommercesupport.payment.service.PaymentService;
import com.ecommerce.aicommercesupport.shipment.controller.ShipmentController;
import com.ecommerce.aicommercesupport.shipment.repository.ShipmentRepository;
import com.ecommerce.aicommercesupport.shipment.service.ShipmentService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CommerceCrudProfileTests {

    @ParameterizedTest
    @ValueSource(strings = {"default", "dev", "test", "prod"})
    void registersCrudControllersAndServicesInEveryProfile(String profile) {
        new ApplicationContextRunner()
                .withPropertyValues("spring.profiles.active=" + profile)
                .withBean(OrderRepository.class, () -> mock(OrderRepository.class))
                .withBean(OrderItemRepository.class, () -> mock(OrderItemRepository.class))
                .withBean(PaymentRepository.class, () -> mock(PaymentRepository.class))
                .withBean(ShipmentRepository.class, () -> mock(ShipmentRepository.class))
                .withBean(CacheManager.class, ConcurrentMapCacheManager::new)
                .withUserConfiguration(OrderController.class, OrderItemController.class, PaymentController.class,
                        ShipmentController.class, OrderService.class, OrderItemService.class,
                        PaymentService.class, ShipmentService.class, CacheEviction.class, CurrentUserId.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(OrderController.class).hasSingleBean(OrderItemController.class)
                            .hasSingleBean(PaymentController.class).hasSingleBean(ShipmentController.class)
                            .hasSingleBean(OrderService.class).hasSingleBean(OrderItemService.class)
                            .hasSingleBean(PaymentService.class).hasSingleBean(ShipmentService.class);
                });
    }
}
