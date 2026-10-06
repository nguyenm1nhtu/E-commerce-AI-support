package com.ecommerce.aicommercesupport.ticket.entity;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import com.ecommerce.aicommercesupport.user.entity.User;
import com.ecommerce.aicommercesupport.user.entity.UserRole;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TicketPersistenceTests {

    private static final Instant CREATED_AT = Instant.parse("2026-10-05T08:00:00Z");

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private Validator validator;

    @Test
    void persistsTicketAssignmentMessagesAndTimestampsWithLazyRelationship() {
        var customer = persistUser(UserRole.CUSTOMER);
        var agent = persistUser(UserRole.SUPPORT_AGENT);
        var ticket = new Ticket(customer.getId(), "SHIPMENT", TicketStatus.HUMAN_HANDLING,
                TicketPriority.HIGH, agent.getId(), CREATED_AT);
        entityManager.persist(ticket);
        var message = new TicketMessage(ticket, TicketSenderType.CUSTOMER, "Đơn hàng của tôi đang ở đâu?", CREATED_AT);
        var reply = new TicketMessage(ticket, TicketSenderType.SUPPORT_AGENT, "Tôi sẽ kiểm tra giúp bạn.", CREATED_AT.plusSeconds(30));
        entityManager.persist(message);
        entityManager.persist(reply);
        entityManager.flush();
        entityManager.clear();

        assertThat(ticket.getId()).isNotNull();
        assertThat(message.getId()).isNotNull().isNotEqualTo(reply.getId());
        var loadedMessage = entityManager.find(TicketMessage.class, message.getId());
        assertThat(entityManager.getEntityManagerFactory().getPersistenceUnitUtil().isLoaded(loadedMessage, "ticket"))
                .isFalse();
        assertThat(loadedMessage.getTicket().getId()).isEqualTo(ticket.getId());
        assertThat(loadedMessage.getSenderType()).isEqualTo(TicketSenderType.CUSTOMER);
        assertThat(loadedMessage.getContent()).isEqualTo("Đơn hàng của tôi đang ở đâu?");
        assertThat(loadedMessage.getCreatedAt()).isEqualTo(CREATED_AT);
        var loadedTicket = entityManager.find(Ticket.class, ticket.getId());
        assertThat(loadedTicket.getUserId()).isEqualTo(customer.getId());
        assertThat(loadedTicket.getAssignedTo()).isEqualTo(agent.getId());
        assertThat(loadedTicket.getCategory()).isEqualTo("SHIPMENT");
        assertThat(loadedTicket.getStatus()).isEqualTo(TicketStatus.HUMAN_HANDLING);
        assertThat(loadedTicket.getPriority()).isEqualTo(TicketPriority.HIGH);
        assertThat(loadedTicket.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(entityManager.find(TicketMessage.class, reply.getId()).getCreatedAt()).isEqualTo(CREATED_AT.plusSeconds(30));
    }

    @Test
    void persistsEveryEnumValueAsStringAndAllowsUnassignedTicket() {
        var customer = persistUser(UserRole.CUSTOMER);
        for (var status : TicketStatus.values()) {
            var ticket = new Ticket(customer.getId(), "ORDER", status, TicketPriority.NORMAL, null, CREATED_AT);
            entityManager.persist(ticket);
            entityManager.flush();
            assertThat(jdbc.queryForObject("SELECT status FROM tickets WHERE id = ?", String.class, ticket.getId()))
                    .isEqualTo(status.name());
            assertThat(ticket.getAssignedTo()).isNull();
        }
        var ticket = persistTicket(customer.getId());
        for (var priority : TicketPriority.values()) {
            ticket.setPriority(priority);
            entityManager.flush();
            assertThat(jdbc.queryForObject("SELECT priority FROM tickets WHERE id = ?", String.class, ticket.getId()))
                    .isEqualTo(priority.name());
        }
        for (var sender : TicketSenderType.values()) {
            var message = new TicketMessage(ticket, sender, "Message from " + sender, CREATED_AT);
            entityManager.persist(message);
            entityManager.flush();
            assertThat(jdbc.queryForObject("SELECT sender_type FROM ticket_messages WHERE id = ?", String.class, message.getId()))
                    .isEqualTo(sender.name());
        }
    }

    @Test
    void validatesRequiredFieldsAndTextLimits() {
        assertThat(validator.validate(new Ticket(null, " ", null, null, null, null)))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactlyInAnyOrder("userId", "category", "status", "priority", "createdAt");
        var ticket = new Ticket(UUID.randomUUID(), "a".repeat(64), TicketStatus.OPEN, TicketPriority.NORMAL, null, CREATED_AT);
        assertThat(validator.validate(ticket)).isEmpty();
        ticket.setCategory("a".repeat(65));
        assertThat(validator.validate(ticket)).extracting(violation -> violation.getPropertyPath().toString()).contains("category");
        assertThat(validator.validate(new TicketMessage(null, null, " ", null)))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactlyInAnyOrder("ticket", "senderType", "content", "createdAt");
        assertThat(validator.validate(new TicketMessage(ticket, TicketSenderType.AI, "a".repeat(10000), CREATED_AT))).isEmpty();
        assertThat(validator.validate(new TicketMessage(ticket, TicketSenderType.AI, "a".repeat(10001), CREATED_AT)))
                .extracting(violation -> violation.getPropertyPath().toString()).contains("content");
    }

    @Test
    void databaseRejectsInvalidTicketValuesAndMissingUsers() {
        var userId = persistUser(UserRole.CUSTOMER).getId();
        assertInvalidTicket(null, "ORDER", "OPEN", "NORMAL", null, CREATED_AT);
        assertInvalidTicket(UUID.randomUUID(), "ORDER", "OPEN", "NORMAL", null, CREATED_AT);
        assertInvalidTicket(userId, "ORDER", "OPEN", "NORMAL", UUID.randomUUID(), CREATED_AT);
        assertInvalidTicket(userId, null, "OPEN", "NORMAL", null, CREATED_AT);
        assertInvalidTicket(userId, " ", "OPEN", "NORMAL", null, CREATED_AT);
        assertInvalidTicket(userId, "a".repeat(65), "OPEN", "NORMAL", null, CREATED_AT);
        assertInvalidTicket(userId, "ORDER", "UNKNOWN", "NORMAL", null, CREATED_AT);
        assertInvalidTicket(userId, "ORDER", null, "NORMAL", null, CREATED_AT);
        assertInvalidTicket(userId, "ORDER", "OPEN", "UNKNOWN", null, CREATED_AT);
        assertInvalidTicket(userId, "ORDER", "OPEN", null, null, CREATED_AT);
        assertInvalidTicket(userId, "ORDER", "OPEN", "NORMAL", null, null);
    }

    @Test
    void databaseRejectsInvalidMessagesAndDoesNotCascadeDeleteConversation() {
        var customer = persistUser(UserRole.CUSTOMER);
        var ticket = persistTicket(customer.getId());
        assertInvalidMessage(null, "CUSTOMER", "Question", CREATED_AT);
        assertInvalidMessage(UUID.randomUUID(), "CUSTOMER", "Question", CREATED_AT);
        assertInvalidMessage(ticket.getId(), null, "Question", CREATED_AT);
        assertInvalidMessage(ticket.getId(), "UNKNOWN", "Question", CREATED_AT);
        assertInvalidMessage(ticket.getId(), "CUSTOMER", null, CREATED_AT);
        assertInvalidMessage(ticket.getId(), "CUSTOMER", " ", CREATED_AT);
        assertInvalidMessage(ticket.getId(), "CUSTOMER", "a".repeat(10001), CREATED_AT);
        assertInvalidMessage(ticket.getId(), "CUSTOMER", "Question", null);
        entityManager.persist(new TicketMessage(ticket, TicketSenderType.CUSTOMER, "Question", CREATED_AT));
        entityManager.flush();
        assertThatThrownBy(() -> jdbc.update("DELETE FROM tickets WHERE id = ?", ticket.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM users WHERE id = ?", customer.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private User persistUser(UserRole role) {
        var user = new User(UUID.randomUUID() + "@example.com", "encoded-password", role, "Minh", "Nguyen");
        entityManager.persist(user);
        entityManager.flush();
        return user;
    }

    private Ticket persistTicket(UUID userId) {
        var ticket = new Ticket(userId, "ORDER", TicketStatus.OPEN, TicketPriority.NORMAL, null, CREATED_AT);
        entityManager.persist(ticket);
        entityManager.flush();
        return ticket;
    }

    private void assertInvalidTicket(UUID userId, String category, String status, String priority, UUID assignedTo, Instant createdAt) {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO tickets (id, user_id, category, status, priority, assigned_to, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), userId, category, status, priority, assignedTo, createdAt == null ? null : Timestamp.from(createdAt)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void assertInvalidMessage(UUID ticketId, String sender, String content, Instant createdAt) {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO ticket_messages (id, ticket_id, sender_type, content, created_at) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), ticketId, sender, content, createdAt == null ? null : Timestamp.from(createdAt)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
