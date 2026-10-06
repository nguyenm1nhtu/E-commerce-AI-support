package com.ecommerce.aicommercesupport.ticket.service;

import java.util.UUID;
import java.util.Optional;
import java.util.stream.Stream;

import com.ecommerce.aicommercesupport.ticket.entity.TicketCategory;

import com.ecommerce.aicommercesupport.common.exception.ResourceNotFoundException;
import com.ecommerce.aicommercesupport.ticket.dto.CreateTicketRequest;
import com.ecommerce.aicommercesupport.ticket.repository.TicketMessageRepository;
import com.ecommerce.aicommercesupport.ticket.repository.TicketRepository;
import com.ecommerce.aicommercesupport.user.repository.UserRepository;
import com.ecommerce.aicommercesupport.user.entity.User;
import com.ecommerce.aicommercesupport.user.entity.UserRole;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.server.ResponseStatusException;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketServiceTests {

    private static ValidatorFactory validatorFactory;

    @BeforeAll
    static void initializeValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TicketMessageRepository ticketMessageRepository;
    private TicketService ticketService;

    @BeforeEach
    void createService() {
        ticketService = new TicketService(ticketRepository, userRepository, ticketMessageRepository,
                validatorFactory.getValidator());
    }

    @Test
    void rejectsMissingUserBeforeWritingTicketOrMessage() {
        var userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.createTicket(userId, new CreateTicketRequest(TicketCategory.ORDER, "Question")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found: " + userId);
        verifyNoInteractions(ticketRepository, ticketMessageRepository);
    }

    @Test
    void rejectsMissingIdentityBeforeAccessingDatabase() {
        assertThatThrownBy(() -> ticketService.createTicket(null, validRequest()))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        verifyNoInteractions(userRepository, ticketRepository, ticketMessageRepository);
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void rejectsInvalidRequestBeforeAccessingDatabase(CreateTicketRequest request) {
        assertThatThrownBy(() -> ticketService.createTicket(UUID.randomUUID(), request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(userRepository, ticketRepository, ticketMessageRepository);
    }

    static Stream<CreateTicketRequest> invalidRequests() {
        return Stream.of(null, new CreateTicketRequest(null, "Question"),
                new CreateTicketRequest(TicketCategory.ORDER, null),
                new CreateTicketRequest(TicketCategory.ORDER, ""),
                new CreateTicketRequest(TicketCategory.ORDER, " \t\n"),
                new CreateTicketRequest(TicketCategory.ORDER, "a".repeat(10001)));
    }

    @Test
    void deniesSupportAgentBeforeWritingTicket() {
        var userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(UserRole.SUPPORT_AGENT)));
        assertThatThrownBy(() -> ticketService.createTicket(userId, validRequest())).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(ticketRepository, ticketMessageRepository);
    }

    @ParameterizedTest
    @MethodSource("storageFailures")
    void translatesUnavailableStorageWithoutExposingDatabaseDetails(RuntimeException failure) {
        var userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenThrow(failure);
        assertThatThrownBy(() -> ticketService.createTicket(userId, validRequest()))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> {
                    assertThat(error.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(error.getReason()).isEqualTo("Ticket storage is temporarily unavailable");
                });
        verifyNoInteractions(ticketRepository, ticketMessageRepository);
    }

    static Stream<RuntimeException> storageFailures() {
        return Stream.of(new DataAccessResourceFailureException("internal connection details"),
                new QueryTimeoutException("internal SQL details"));
    }

    @Test
    void translatesOwnerDeletionRaceToConflict() {
        var userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(UserRole.CUSTOMER)));
        when(ticketRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("fk_tickets_user"));
        assertThatThrownBy(() -> ticketService.createTicket(userId, validRequest()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verifyNoInteractions(ticketMessageRepository);
    }

    @Test
    void doesNotMislabelUnexpectedIntegrityFailureAsClientError() {
        var userId = UUID.randomUUID();
        var failure = new DataIntegrityViolationException("Unexpected schema constraint");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(UserRole.CUSTOMER)));
        when(ticketRepository.saveAndFlush(any())).thenThrow(failure);
        assertThatThrownBy(() -> ticketService.createTicket(userId, validRequest())).isSameAs(failure);
    }

    private CreateTicketRequest validRequest() {
        return new CreateTicketRequest(TicketCategory.ORDER, "Question");
    }

    private User user(UserRole role) {
        return new User("customer@example.com", "encoded-password", role, "Minh", "Nguyen");
    }
}
