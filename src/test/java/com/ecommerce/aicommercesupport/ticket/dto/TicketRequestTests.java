package com.ecommerce.aicommercesupport.ticket.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class TicketRequestTests {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void initializeValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @Test
    void acceptsValidRequestsAndMaximumLengths() {
        assertThat(validator.validate(new CreateTicketRequest("SHIPMENT", "Đơn hàng đang ở đâu?"))).isEmpty();
        assertThat(validator.validate(new CreateTicketRequest("a".repeat(64), "a".repeat(10000)))).isEmpty();
        assertThat(validator.validate(new CreateTicketMessageRequest("a".repeat(10000)))).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsMissingOrBlankCategoryAndContent(String value) {
        assertThat(validator.validate(new CreateTicketRequest(value, "Question")))
                .extracting(violation -> violation.getPropertyPath().toString()).contains("category");
        assertThat(validator.validate(new CreateTicketRequest("ORDER", value)))
                .extracting(violation -> violation.getPropertyPath().toString()).contains("content");
        assertThat(validator.validate(new CreateTicketMessageRequest(value)))
                .extracting(violation -> violation.getPropertyPath().toString()).contains("content");
    }

    @Test
    void rejectsValuesOverEntityLimits() {
        assertThat(validator.validate(new CreateTicketRequest("a".repeat(65), "a".repeat(10001))))
                .extracting(violation -> violation.getPropertyPath().toString()).containsExactlyInAnyOrder("category", "content");
        assertThat(validator.validate(new CreateTicketMessageRequest("a".repeat(10001))))
                .extracting(violation -> violation.getPropertyPath().toString()).containsExactly("content");
    }

    @Test
    void requestContractsContainOnlyCustomerInputAndRoundTripJson() {
        var mapper = JsonMapper.builder().build();
        var request = new CreateTicketRequest("ORDER", "Question");
        var json = mapper.writeValueAsString(request);
        assertThat(mapper.readTree(json).size()).isEqualTo(2);
        assertThat(mapper.readValue(json, CreateTicketRequest.class)).isEqualTo(request);
        var message = new CreateTicketMessageRequest("More details");
        var messageJson = mapper.writeValueAsString(message);
        assertThat(mapper.readTree(messageJson).size()).isEqualTo(1);
        assertThat(mapper.readValue(messageJson, CreateTicketMessageRequest.class)).isEqualTo(message);
    }
}
