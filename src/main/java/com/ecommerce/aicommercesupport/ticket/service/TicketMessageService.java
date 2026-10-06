package com.ecommerce.aicommercesupport.ticket.service;

import java.util.Set;
import java.util.UUID;

import com.ecommerce.aicommercesupport.ticket.dto.TicketMessageDto;
import com.ecommerce.aicommercesupport.ticket.entity.TicketMessage;
import com.ecommerce.aicommercesupport.ticket.repository.TicketMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TicketMessageService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> SORT_FIELDS = Set.of("createdAt", "id");

    private final TicketService ticketService;
    private final TicketMessageRepository ticketMessageRepository;

    public Page<TicketMessageDto> getMessagesByTicketIdAndUserId(UUID ticketId, UUID userId, Pageable pageable) {
        ticketService.requireTicketOwnership(ticketId, userId);
        var requestedPage = validatePageable(pageable);
        try {
            return ticketMessageRepository.findByTicket_IdAndTicket_UserId(ticketId, userId, requestedPage)
                    .map(this::toDto);
        } catch (DataAccessResourceFailureException | QueryTimeoutException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Ticket storage is temporarily unavailable", exception);
        }
    }

    private Pageable validatePageable(Pageable pageable) {
        if (pageable == null || pageable.isUnpaged() || pageable.getPageSize() > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A page size between 1 and 100 is required");
        }
        if (pageable.getSort().stream().anyMatch(order -> !SORT_FIELDS.contains(order.getProperty()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported message sort field");
        }
        var sort = pageable.getSort().isUnsorted() ? Sort.by("createdAt", "id") : pageable.getSort();
        if (sort.getOrderFor("id") == null) {
            sort = sort.and(Sort.by("id"));
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }

    private TicketMessageDto toDto(TicketMessage message) {
        return new TicketMessageDto(message.getId(), message.getTicket().getId(), message.getSenderType(),
                message.getContent(), message.getCreatedAt());
    }
}
