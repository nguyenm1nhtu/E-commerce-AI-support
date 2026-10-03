package com.ecommerce.aicommercesupport.order.dto;

import java.util.List;

import com.ecommerce.aicommercesupport.payment.dto.PaymentDto;
import com.ecommerce.aicommercesupport.shipment.dto.ShipmentDto;

public record OrderDetailDto(
        OrderDto order,
        List<OrderItemDto> items,
        PaymentDto payment,
        ShipmentDto shipment
) {
    public OrderDetailDto {
        items = List.copyOf(items);
    }
}
