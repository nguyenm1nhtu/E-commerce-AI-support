package com.ecommerce.aicommercesupport.ticket.dto;

import com.ecommerce.aicommercesupport.ticket.entity.TicketCategory;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.core.JacksonException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        assertThat(validator.validate(new CreateTicketRequest(TicketCategory.SHIPMENT, "Đơn hàng đang ở đâu?"))).isEmpty();
        assertThat(validator.validate(new CreateTicketRequest(TicketCategory.OTHER, "a".repeat(10000)))).isEmpty();
        assertThat(validator.validate(new CreateTicketMessageRequest("a".repeat(10000)))).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsMissingOrBlankContent(String value) {
        assertThat(validator.validate(new CreateTicketRequest(TicketCategory.ORDER, value)))
                .extracting(violation -> violation.getPropertyPath().toString()).contains("content");
        assertThat(validator.validate(new CreateTicketMessageRequest(value)))
                .extracting(violation -> violation.getPropertyPath().toString()).contains("content");
    }

    @Test
    void rejectsValuesOverEntityLimits() {
        assertThat(validator.validate(new CreateTicketRequest(TicketCategory.ORDER, "a".repeat(10001))))
                .extracting(violation -> violation.getPropertyPath().toString()).containsExactly("content");
        assertThat(validator.validate(new CreateTicketMessageRequest("a".repeat(10001))))
                .extracting(violation -> violation.getPropertyPath().toString()).containsExactly("content");
    }

    @Test
    void requestContractsContainOnlyCustomerInputAndRoundTripJson() {
        var mapper = JsonMapper.builder().build();
        var request = new CreateTicketRequest(TicketCategory.ORDER, "Question");
        var json = mapper.writeValueAsString(request);
        assertThat(mapper.readTree(json).size()).isEqualTo(2);
        assertThat(mapper.readValue(json, CreateTicketRequest.class)).isEqualTo(request);
        var message = new CreateTicketMessageRequest("More details");
        var messageJson = mapper.writeValueAsString(message);
        assertThat(mapper.readTree(messageJson).size()).isEqualTo(1);
        assertThat(mapper.readValue(messageJson, CreateTicketMessageRequest.class)).isEqualTo(message);
    }

    @Test
    void requiresCategoryWhenJsonFieldIsMissingOrNull() {
        var mapper = JsonMapper.builder().build();
        for (var json : new String[] {"{\"content\":\"Question\"}", "{\"category\":null,\"content\":\"Question\"}"}) {
            assertThat(validator.validate(mapper.readValue(json, CreateTicketRequest.class)))
                    .extracting(violation -> violation.getPropertyPath().toString()).containsExactly("category");
        }
    }

    @ParameterizedTest
    @EnumSource(TicketCategory.class)
    void acceptsEverySupportedCategoryAsJsonString(TicketCategory category) {
        var mapper = JsonMapper.builder().build();
        var request = mapper.readValue("{\"category\":\"" + category.name() + "\",\"content\":\"Question\"}", CreateTicketRequest.class);
        assertThat(request.category()).isEqualTo(category);
        assertThat(validator.validate(request)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNKNOWN", "order", "CUSTOM_CATEGORY"})
    void rejectsCategoryOutsideTheEnum(String category) {
        var mapper = JsonMapper.builder().build();
        assertThatThrownBy(() -> mapper.readValue("{\"category\":\"" + category + "\",\"content\":\"Question\"}", CreateTicketRequest.class))
                .isInstanceOf(JacksonException.class);
    }
}
