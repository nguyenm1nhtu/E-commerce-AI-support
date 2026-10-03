package com.ecommerce.aicommercesupport.shipment.entity;

import java.time.Instant;
import java.util.UUID;

import com.ecommerce.aicommercesupport.order.entity.Order;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "shipments")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Setter(AccessLevel.NONE)
    private UUID id;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ShipmentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 255)
    private Carrier carrier;

    @Size(max = 255)
    private String trackingCode;

    private Instant deliveredAt;

    public Shipment(Order order, ShipmentStatus status, Carrier carrier, String trackingCode, Instant deliveredAt) {
        this.order = order;
        this.status = status;
        this.carrier = carrier;
        this.trackingCode = trackingCode;
        this.deliveredAt = deliveredAt;
    }
}
