package com.ecommerce.aicommercesupport.ticket.repository;

import java.time.Instant;
import java.util.UUID;

import com.ecommerce.aicommercesupport.ticket.entity.TicketCategory;

import com.ecommerce.aicommercesupport.ticket.entity.Ticket;
import com.ecommerce.aicommercesupport.ticket.entity.TicketMessage;
import com.ecommerce.aicommercesupport.ticket.entity.TicketPriority;
import com.ecommerce.aicommercesupport.ticket.entity.TicketSenderType;
import com.ecommerce.aicommercesupport.ticket.entity.TicketStatus;
import com.ecommerce.aicommercesupport.user.entity.User;
import com.ecommerce.aicommercesupport.user.entity.UserRole;
import com.ecommerce.aicommercesupport.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TicketMessageRepositoryTests {

    @Autowired
    private TicketRepository tickets;
    @Autowired
    private TicketMessageRepository messages;
    @Autowired
    private UserRepository users;

    @Test
    void pagesMessagesInChronologicalOrderAndSeparatesTicketsAndOwners() {
        var owner = users.saveAndFlush(new User(UUID.randomUUID() + "@example.com", "encoded-password",
                UserRole.CUSTOMER, "Minh", "Nguyen")).getId();
        var timestamp = Instant.parse("2026-10-05T00:00:00Z");
        var ticket = tickets.saveAndFlush(new Ticket(owner, TicketCategory.ORDER, TicketStatus.OPEN, TicketPriority.NORMAL, null, timestamp));
        var otherTicket = tickets.saveAndFlush(new Ticket(owner, TicketCategory.PAYMENT, TicketStatus.OPEN, TicketPriority.NORMAL, null, timestamp));
        var first = messages.saveAndFlush(new TicketMessage(ticket, TicketSenderType.CUSTOMER, "Question", timestamp));
        var second = messages.saveAndFlush(new TicketMessage(ticket, TicketSenderType.AI, "Answer", timestamp.plusSeconds(1)));
        messages.saveAndFlush(new TicketMessage(otherTicket, TicketSenderType.CUSTOMER, "Other conversation", timestamp));
        var page = PageRequest.of(0, 1, Sort.by("createdAt", "id"));

        var result = messages.findByTicket_Id(ticket.getId(), page);
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).extracting(TicketMessage::getId).containsExactly(first.getId());
        assertThat(messages.findByTicket_Id(ticket.getId(), page.next()).getContent())
                .extracting(TicketMessage::getId).containsExactly(second.getId());
        assertThat(messages.findByTicket_IdAndTicket_UserId(ticket.getId(), owner, page).getTotalElements()).isEqualTo(2);
        assertThat(messages.findByTicket_IdAndTicket_UserId(ticket.getId(), UUID.randomUUID(), page)).isEmpty();
        assertThat(messages.findByTicket_Id(UUID.randomUUID(), page)).isEmpty();
        assertThat(messages.findByIdAndTicket_Id(first.getId(), ticket.getId())).isPresent();
        assertThat(messages.findByIdAndTicket_Id(first.getId(), otherTicket.getId())).isEmpty();
        assertThat(messages.findByIdAndTicket_Id(UUID.randomUUID(), ticket.getId())).isEmpty();
    }
}
