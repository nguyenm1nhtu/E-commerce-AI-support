package com.ecommerce.aicommercesupport.ticket.service;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import com.ecommerce.aicommercesupport.common.exception.ResourceNotFoundException;
import com.ecommerce.aicommercesupport.ticket.dto.CreateTicketMessageRequest;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("test")
class TicketWorkflowIntegrationTests {
    @Autowired private TicketService tickets;
    @Autowired private TicketWorkflowService workflow;
    @Autowired private TicketMessageService conversation;
    @MockitoSpyBean private TicketRepository repository;
    @MockitoSpyBean private TicketMessageRepository messages;
    @Autowired private UserRepository users;

    private UUID owner;
    private UUID other;
    private UUID agent;
    private UUID otherAgent;
    private UUID ticketId;
    private final Pageable page = PageRequest.of(0, 10);

    @BeforeEach
    void setup() {
        owner = user(UserRole.CUSTOMER);
        other = user(UserRole.CUSTOMER);
        agent = user(UserRole.SUPPORT_AGENT);
        otherAgent = user(UserRole.SUPPORT_AGENT);
        ticketId = tickets.createTicket(owner, new CreateTicketRequest(TicketCategory.ORDER, "Where is my order?")).id();
    }

    private UUID user(UserRole role) {
        return users.saveAndFlush(new User(UUID.randomUUID() + "@example.com", "encoded", role, "Test", "User")).getId();
    }

    @AfterEach
    void cleanup() {
        for (var ticket : repository.findByUserId(owner, Pageable.unpaged())) {
            messages.deleteAll(messages.findByTicket_Id(ticket.getId(), Pageable.unpaged()).getContent());
            repository.deleteById(ticket.getId());
        }
        users.deleteAllById(java.util.List.of(owner, other, agent, otherAgent));
    }

    @Test
    void completesAiConversationAndRejectsWritesAfterResolution() {
        assertThat(workflow.startAiHandling(ticketId, owner).status()).isEqualTo(TicketStatus.AI_HANDLING);
        assertThat(workflow.recordAiReply(ticketId, owner, message("Please confirm the address")).status())
                .isEqualTo(TicketStatus.WAITING_CUSTOMER);
        var reply = conversation.addMessage(ticketId, owner, message("Address confirmed"));
        assertThat(reply.senderType()).isEqualTo(TicketSenderType.CUSTOMER);
        assertThat(tickets.getTicketByIdAndUserId(ticketId, owner).status()).isEqualTo(TicketStatus.AI_HANDLING);
        workflow.recordAiReply(ticketId, owner, message("Your parcel is on its way"));
        assertThat(workflow.resolveTicket(ticketId, owner).status()).isEqualTo(TicketStatus.RESOLVED);
        assertConflict(() -> conversation.addMessage(ticketId, owner, message("Again")));
        assertConflict(() -> workflow.startAiHandling(ticketId, owner));
        assertConflict(() -> workflow.escalateTicket(ticketId, owner));
        assertThat(conversation.getMessagesByTicketIdAndUserId(ticketId, owner, page).getTotalElements()).isEqualTo(4);
    }

    @Test
    void completesHumanHandoffAndRestrictsOtherAgents() {
        workflow.startAiHandling(ticketId, owner);
        workflow.escalateTicket(ticketId, owner);
        assertThat(workflow.getEscalationQueue(agent, page).getContent()).extracting("id").contains(ticketId);
        assertThat(workflow.claimTicket(ticketId, agent).assignedTo()).isEqualTo(agent);
        assertConflict(() -> workflow.claimTicket(ticketId, otherAgent));
        assertThat(workflow.getAssignedTicket(ticketId, agent).status()).isEqualTo(TicketStatus.HUMAN_HANDLING);
        assertThat(workflow.getAssignedTickets(agent, TicketStatus.HUMAN_HANDLING, page).getTotalElements()).isEqualTo(1);
        assertThat(workflow.getAssignedTickets(otherAgent, TicketStatus.HUMAN_HANDLING, page)).isEmpty();
        assertThatThrownBy(() -> conversation.addMessage(ticketId, otherAgent, message("Unauthorized")))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> workflow.resolveTicket(ticketId, otherAgent)).isInstanceOf(ResourceNotFoundException.class);
        assertThat(workflow.updatePriority(ticketId, agent, TicketPriority.HIGH).priority()).isEqualTo(TicketPriority.HIGH);
        assertThat(conversation.addMessage(ticketId, agent, message("Need more details")).senderType())
                .isEqualTo(TicketSenderType.SUPPORT_AGENT);
        assertThat(tickets.getTicketByIdAndUserId(ticketId, owner).status()).isEqualTo(TicketStatus.WAITING_CUSTOMER);
        conversation.addMessage(ticketId, owner, message("Here are the details"));
        assertThat(workflow.getAssignedTicket(ticketId, agent).status()).isEqualTo(TicketStatus.HUMAN_HANDLING);
        assertThat(conversation.getMessagesForSupport(ticketId, agent, page).getTotalElements()).isEqualTo(3);
        assertThat(workflow.resolveTicket(ticketId, agent).status()).isEqualTo(TicketStatus.RESOLVED);
    }

    @Test
    void listsOnlyOwnedTicketsWithOptionalStatusAndStablePagination() {
        tickets.createTicket(owner, new CreateTicketRequest(TicketCategory.OTHER, "Another question"));
        workflow.escalateTicket(ticketId, owner);
        assertThat(tickets.getTicketsByUserId(owner, null, PageRequest.of(0, 1)).getTotalElements()).isEqualTo(2);
        assertThat(tickets.getTicketsByUserId(owner, TicketStatus.ESCALATED, page).getContent())
                .extracting("id").containsExactly(ticketId);
        assertThat(tickets.getTicketsByUserId(other, null, page)).isEmpty();
        assertThat(tickets.getTicketsByUserId(owner, null, page).getSort().getOrderFor("id")).isNotNull();
    }

    @Test
    void deniesMissingIdentityWrongRoleAndForeignOwnership() {
        assertThatThrownBy(() -> conversation.addMessage(ticketId, null, message("No identity")))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThatThrownBy(() -> workflow.getEscalationQueue(owner, page)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> workflow.claimTicket(ticketId, owner)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> workflow.startAiHandling(ticketId, agent)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> tickets.getTicketByIdAndUserId(ticketId, other)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> conversation.getMessagesByTicketIdAndUserId(ticketId, other, page))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> conversation.addMessage(ticketId, other, message("Foreign")))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> workflow.escalateTicket(ticketId, other)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> conversation.getMessagesForSupport(ticketId, otherAgent, page))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void checksMessageBelongsToRequestedTicketAndDistinguishesMissingTicket() {
        var first = conversation.getMessagesByTicketIdAndUserId(ticketId, owner, page).getContent().getFirst();
        assertThat(conversation.getMessage(ticketId, first.id(), owner)).isEqualTo(first);
        var another = tickets.createTicket(owner, new CreateTicketRequest(TicketCategory.OTHER, "Other")).id();
        assertThatThrownBy(() -> conversation.getMessage(another, first.id(), owner)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> conversation.getMessagesByTicketIdAndUserId(UUID.randomUUID(), owner, page))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @ParameterizedTest
    @MethodSource("invalidMessages")
    void rejectsInvalidMessagesWithoutChangingState(CreateTicketMessageRequest request) {
        assertThatThrownBy(() -> conversation.addMessage(ticketId, owner, request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThat(repository.findById(ticketId).orElseThrow().getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(messages.findByTicket_Id(ticketId, page).getTotalElements()).isEqualTo(1);
    }

    static Stream<CreateTicketMessageRequest> invalidMessages() {
        return Stream.of(null, new CreateTicketMessageRequest(null), new CreateTicketMessageRequest(" "),
                new CreateTicketMessageRequest("a".repeat(10001)));
    }

    @ParameterizedTest
    @MethodSource("invalidPages")
    void rejectsUnboundedAndUnsupportedPages(Pageable invalid) {
        assertThatThrownBy(() -> tickets.getTicketsByUserId(owner, null, invalid))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> conversation.getMessagesByTicketIdAndUserId(ticketId, owner, invalid))
                .isInstanceOf(ResponseStatusException.class);
    }

    static Stream<Pageable> invalidPages() {
        return Stream.of(null, Pageable.unpaged(), PageRequest.of(0, 101), PageRequest.of(0, 10, Sort.by("content")));
    }

    @Test
    void acceptsMaximumMessageLengthAndStartsHandling() {
        assertThat(conversation.addMessage(ticketId, owner, message("a".repeat(10000))).content()).hasSize(10000);
        assertThat(repository.findById(ticketId).orElseThrow().getStatus()).isEqualTo(TicketStatus.AI_HANDLING);
    }

    @Test
    void rejectsInvalidTransitionsAndLateAiReplyAfterEscalation() {
        assertConflict(() -> workflow.resolveTicket(ticketId, owner));
        assertConflict(() -> workflow.claimTicket(ticketId, agent));
        workflow.startAiHandling(ticketId, owner);
        assertConflict(() -> workflow.startAiHandling(ticketId, owner));
        workflow.escalateTicket(ticketId, owner);
        assertConflict(() -> workflow.recordAiReply(ticketId, owner, message("Late answer")));
        assertThat(messages.findByTicket_Id(ticketId, page).getTotalElements()).isEqualTo(1);
    }

    @Test
    void preservesStateWhenAiMessageSaveFails() {
        workflow.startAiHandling(ticketId, owner);
        doThrow(new DataIntegrityViolationException("Simulated message failure")).when(messages).saveAndFlush(any());
        assertThatThrownBy(() -> workflow.recordAiReply(ticketId, owner, message("Answer")))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(repository.findById(ticketId).orElseThrow().getStatus()).isEqualTo(TicketStatus.AI_HANDLING);
        assertThat(messages.findByTicket_Id(ticketId, page).getTotalElements()).isEqualTo(1);
    }

    @Test
    void rollsBackAlreadyFlushedAiMessageWhenStateSaveFails() {
        workflow.startAiHandling(ticketId, owner);
        doThrow(new DataIntegrityViolationException("Simulated ticket failure")).when(repository).saveAndFlush(any());
        assertThatThrownBy(() -> workflow.recordAiReply(ticketId, owner, message("Answer")))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(repository.findById(ticketId).orElseThrow().getStatus()).isEqualTo(TicketStatus.AI_HANDLING);
        assertThat(messages.findByTicket_Id(ticketId, page).getTotalElements()).isEqualTo(1);
    }

    @Test
    void onlyOneAgentCanClaimTicketConcurrently() throws Exception {
        workflow.escalateTicket(ticketId, owner);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> claimAfter(start, agent));
            var second = executor.submit(() -> claimAfter(start, otherAgent));
            start.countDown();
            assertThat(java.util.List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(repository.findById(ticketId).orElseThrow().getAssignedTo()).isIn(agent, otherAgent);
    }

    private boolean claimAfter(CountDownLatch start, UUID agentId) throws InterruptedException {
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent claim did not start");
        }
        try {
            workflow.claimTicket(ticketId, agentId);
            return true;
        } catch (ResponseStatusException exception) {
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            return false;
        }
    }

    private CreateTicketMessageRequest message(String content) {
        return new CreateTicketMessageRequest(content);
    }

    private void assertConflict(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }
}
