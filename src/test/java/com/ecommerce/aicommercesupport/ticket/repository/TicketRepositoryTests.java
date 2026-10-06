package com.ecommerce.aicommercesupport.ticket.repository;

import java.time.Instant;
import java.util.UUID;

import com.ecommerce.aicommercesupport.ticket.entity.Ticket;
import com.ecommerce.aicommercesupport.ticket.entity.TicketPriority;
import com.ecommerce.aicommercesupport.ticket.entity.TicketStatus;
import com.ecommerce.aicommercesupport.user.entity.User;
import com.ecommerce.aicommercesupport.user.entity.UserRole;
import com.ecommerce.aicommercesupport.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TicketRepositoryTests {

    @Autowired
    private TicketRepository tickets;
    @Autowired
    private UserRepository users;
    @Autowired
    private MockMvc mvc;

    @Test
    void pagesOnlyOwnersTicketsAndFiltersStatus() {
        var owner = saveUser(UserRole.CUSTOMER);
        var other = saveUser(UserRole.CUSTOMER);
        var older = saveTicket(owner, TicketStatus.OPEN, null, 0);
        var newer = saveTicket(owner, TicketStatus.RESOLVED, null, 60);
        saveTicket(other, TicketStatus.OPEN, null, 120);
        var sort = Sort.by(Sort.Direction.DESC, "createdAt", "id");

        var first = tickets.findByUserId(owner, PageRequest.of(0, 1, sort));
        assertThat(first.getTotalElements()).isEqualTo(2);
        assertThat(first.getTotalPages()).isEqualTo(2);
        assertThat(first.getContent()).extracting(Ticket::getId).containsExactly(newer.getId());
        assertThat(tickets.findByUserId(owner, PageRequest.of(1, 1, sort)).getContent())
                .extracting(Ticket::getId).containsExactly(older.getId());
        assertThat(tickets.findByUserIdAndStatus(owner, TicketStatus.OPEN, PageRequest.of(0, 10)).getContent())
                .extracting(Ticket::getId).containsExactly(older.getId());
        assertThat(tickets.findByUserId(UUID.randomUUID(), PageRequest.of(0, 10))).isEmpty();
        assertThat(tickets.findByIdAndUserId(older.getId(), owner)).isPresent();
        assertThat(tickets.findByIdAndUserId(older.getId(), other)).isEmpty();
        assertThat(tickets.findByIdAndUserId(UUID.randomUUID(), owner)).isEmpty();
    }

    @Test
    void separatesAssignedWorkFromUnassignedQueueAndOtherAgents() {
        var owner = saveUser(UserRole.CUSTOMER);
        var agent = saveUser(UserRole.SUPPORT_AGENT);
        var otherAgent = saveUser(UserRole.SUPPORT_AGENT);
        var assigned = saveTicket(owner, TicketStatus.HUMAN_HANDLING, agent, 0);
        saveTicket(owner, TicketStatus.RESOLVED, agent, 10);
        saveTicket(owner, TicketStatus.HUMAN_HANDLING, otherAgent, 20);
        var waiting = saveTicket(owner, TicketStatus.ESCALATED, null, 30);
        saveTicket(owner, TicketStatus.ESCALATED, agent, 40);
        saveTicket(owner, TicketStatus.OPEN, null, 50);
        var page = PageRequest.of(0, 10, Sort.by("createdAt", "id"));

        assertThat(tickets.findByAssignedToAndStatus(agent, TicketStatus.HUMAN_HANDLING, page).getContent())
                .extracting(Ticket::getId).containsExactly(assigned.getId());
        assertThat(tickets.findByStatusAndAssignedToIsNull(TicketStatus.ESCALATED, page).getContent())
                .extracting(Ticket::getId).containsExactly(waiting.getId());
        assertThat(tickets.findByAssignedToAndStatus(UUID.randomUUID(), TicketStatus.HUMAN_HANDLING, page)).isEmpty();
    }

    @Test
    void doesNotExposeTicketRepositoriesThroughSpringDataRest() throws Exception {
        for (var path : new String[] {"/tickets", "/ticketMessages"}) {
            mvc.perform(get(path).with(user(UUID.randomUUID().toString())))
                    .andExpect(status().isNotFound());
        }
    }

    private UUID saveUser(UserRole role) {
        return users.saveAndFlush(new User(UUID.randomUUID() + "@example.com", "encoded-password", role, "Minh", "Nguyen")).getId();
    }

    private Ticket saveTicket(UUID owner, TicketStatus status, UUID assignedTo, long seconds) {
        return tickets.saveAndFlush(new Ticket(owner, "ORDER", status, TicketPriority.NORMAL, assignedTo,
                Instant.parse("2026-10-05T00:00:00Z").plusSeconds(seconds)));
    }
}
