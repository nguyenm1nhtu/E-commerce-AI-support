package com.ecommerce.aicommercesupport.ticket.dto;

import java.time.Instant;
import java.util.UUID;

import com.ecommerce.aicommercesupport.ticket.entity.TicketPriority;
import com.ecommerce.aicommercesupport.ticket.entity.TicketSenderType;
import com.ecommerce.aicommercesupport.ticket.entity.TicketStatus;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class TicketDtoTests {

    @Test
    void roundTripsTicketIncludingEnumsUuidTimestampAndNullableAssignee() {
        var mapper = JsonMapper.builder().build();
        var dto = new TicketDto(UUID.randomUUID(), UUID.randomUUID(), "ORDER", TicketStatus.OPEN,
                TicketPriority.NORMAL, null, Instant.parse("2026-10-05T00:00:00Z"));
        var json = mapper.writeValueAsString(dto);

        assertThat(mapper.readValue(json, TicketDto.class)).isEqualTo(dto);
        var tree = mapper.readTree(json);
        assertThat(tree.size()).isEqualTo(7);
        assertThat(tree.get("status").asText()).isEqualTo("OPEN");
        assertThat(tree.get("priority").asText()).isEqualTo("NORMAL");
        assertThat(tree.get("assignedTo").isNull()).isTrue();
    }

    @Test
    void messageResponseContainsTicketIdWithoutEmbeddingJpaEntities() {
        var mapper = JsonMapper.builder().build();
        var dto = new TicketMessageDto(UUID.randomUUID(), UUID.randomUUID(), TicketSenderType.AI,
                "Thông tin hỗ trợ", Instant.parse("2026-10-05T00:00:00Z"));
        var json = mapper.writeValueAsString(dto);

        assertThat(mapper.readValue(json, TicketMessageDto.class)).isEqualTo(dto);
        var tree = mapper.readTree(json);
        assertThat(tree.size()).isEqualTo(5);
        assertThat(tree.get("ticketId").asText()).isEqualTo(dto.ticketId().toString());
        assertThat(tree.get("senderType").asText()).isEqualTo("AI");
        assertThat(tree.has("ticket")).isFalse();
    }
}
