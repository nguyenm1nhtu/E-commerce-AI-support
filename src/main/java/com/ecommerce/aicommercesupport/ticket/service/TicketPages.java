package com.ecommerce.aicommercesupport.ticket.service;

import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class TicketPages {
    private TicketPages() {
    }

    static Pageable validate(Pageable pageable, boolean messages) {
        if (pageable == null || pageable.isUnpaged() || pageable.getPageSize() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A page size between 1 and 100 is required");
        }
        var fields = messages ? Set.of("createdAt", "id") : Set.of("createdAt", "id", "status", "priority");
        if (pageable.getSort().stream().anyMatch(order -> !fields.contains(order.getProperty()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported sort field");
        }
        var sort = pageable.getSort();
        if (sort.isUnsorted()) {
            sort = Sort.by(messages ? Sort.Direction.ASC : Sort.Direction.DESC, "createdAt", "id");
        } else if (sort.getOrderFor("id") == null) {
            sort = sort.and(Sort.by("id"));
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }
}
