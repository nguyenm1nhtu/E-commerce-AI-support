package com.ecommerce.aicommercesupport.ticket.service;

import java.util.UUID;
import java.time.temporal.ChronoUnit;

import com.ecommerce.aicommercesupport.ticket.dto.CreateTicketRequest;
import com.ecommerce.aicommercesupport.ticket.entity.TicketCategory;
import com.ecommerce.aicommercesupport.ticket.entity.TicketPriority;
import com.ecommerce.aicommercesupport.ticket.entity.TicketSenderType;
import com.ecommerce.aicommercesupport.ticket.entity.TicketStatus;
import com.ecommerce.aicommercesupport.ticket.repository.TicketMessageRepository;
import com.ecommerce.aicommercesupport.ticket.repository.TicketRepository;
import com.ecommerce.aicommercesupport.user.entity.User;
import com.ecommerce.aicommercesupport.user.entity.UserRole;
import com.ecommerce.aicommercesupport.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("test")
class TicketServiceIntegrationTests {

    @Autowired
    private TicketService service;
    @Autowired
    private TicketRepository tickets;
    @MockitoSpyBean
    private TicketMessageRepository messages;
    @Autowired
    private UserRepository users;

    private UUID userId;

    @BeforeEach
    void createUser() {
        userId = users.saveAndFlush(new User(UUID.randomUUID() + "@example.com", "encoded-password",
                UserRole.CUSTOMER, "Minh", "Nguyen")).getId();
    }

    @AfterEach
    void deleteOwnData() {
        for (var ticket : tickets.findByUserId(userId, Pageable.unpaged())) {
            messages.deleteAll(messages.findByTicket_Id(ticket.getId(), Pageable.unpaged()).getContent());
            tickets.deleteById(ticket.getId());
        }
        users.deleteById(userId);
    }

    @Test
    void commitsTicketAndFirstCustomerMessageAndReturnsSavedTicket() {
        var result = service.createTicket(userId, new CreateTicketRequest(TicketCategory.SHIPMENT, "Đơn hàng của tôi đang ở đâu?"));

        assertThat(result.id()).isNotNull();
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.category()).isEqualTo(TicketCategory.SHIPMENT);
        assertThat(result.status()).isEqualTo(TicketStatus.OPEN);
        assertThat(result.priority()).isEqualTo(TicketPriority.NORMAL);
        assertThat(result.assignedTo()).isNull();
        var saved = tickets.findByIdAndUserId(result.id(), userId).orElseThrow();
        // The database stores microseconds while Instant.now() can include nanoseconds.
        assertThat(saved.getCreatedAt()).isCloseTo(result.createdAt(), within(1, ChronoUnit.MICROS));
        var conversation = messages.findByTicket_Id(result.id(), Pageable.unpaged());
        assertThat(conversation.getTotalElements()).isEqualTo(1);
        var message = conversation.getContent().getFirst();
        assertThat(message.getSenderType()).isEqualTo(TicketSenderType.CUSTOMER);
        assertThat(message.getContent()).isEqualTo("Đơn hàng của tôi đang ở đâu?");
        assertThat(message.getCreatedAt()).isEqualTo(saved.getCreatedAt());
    }

    @Test
    void rollsBackAlreadyFlushedTicketWhenFirstMessageCannotBeSaved() {
        var messageCount = messages.count();
        doThrow(new DataIntegrityViolationException("Simulated message constraint failure"))
                .when(messages).saveAndFlush(any());

        assertThatThrownBy(() -> service.createTicket(userId, new CreateTicketRequest(TicketCategory.ORDER, "Question")))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(tickets.findByUserId(userId, Pageable.unpaged())).isEmpty();
        assertThat(messages.count()).isEqualTo(messageCount);
    }

    @Test
    void rollsBackTicketWhenMessageStorageBecomesUnavailable() {
        doThrow(new DataAccessResourceFailureException("Simulated storage outage"))
                .when(messages).saveAndFlush(any());
        assertThatThrownBy(() -> service.createTicket(userId, new CreateTicketRequest(TicketCategory.ORDER, "Question")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        assertThat(tickets.findByUserId(userId, Pageable.unpaged())).isEmpty();
    }

    @Test
    void acceptsMaximumContentLength() {
        var content = "a".repeat(10000);
        var result = service.createTicket(userId, new CreateTicketRequest(TicketCategory.OTHER, content));
        assertThat(messages.findByTicket_Id(result.id(), Pageable.unpaged()).getContent().getFirst().getContent())
                .isEqualTo(content);
    }
}
