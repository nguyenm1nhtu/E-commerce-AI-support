package com.ecommerce.aicommercesupport.ticket.controller;

import java.util.UUID;
import java.util.stream.Stream;

import com.ecommerce.aicommercesupport.auth.service.JwtService;
import com.ecommerce.aicommercesupport.ticket.dto.CreateTicketRequest;
import com.ecommerce.aicommercesupport.ticket.entity.TicketCategory;
import com.ecommerce.aicommercesupport.ticket.entity.TicketStatus;
import com.ecommerce.aicommercesupport.ticket.repository.TicketMessageRepository;
import com.ecommerce.aicommercesupport.ticket.repository.TicketRepository;
import com.ecommerce.aicommercesupport.ticket.service.TicketService;
import com.ecommerce.aicommercesupport.user.entity.User;
import com.ecommerce.aicommercesupport.user.entity.UserRole;
import com.ecommerce.aicommercesupport.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TicketControllerTests {
    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwt;
    @Autowired private UserRepository users;
    @Autowired private TicketRepository tickets;
    @Autowired private TicketMessageRepository messages;
    @Autowired private TicketService service;

    private UUID owner;
    private UUID other;
    private UUID agent;
    private UUID secondAgent;
    private UUID ticketId;

    @BeforeEach
    void setup() {
        owner = user(UserRole.CUSTOMER);
        other = user(UserRole.CUSTOMER);
        agent = user(UserRole.SUPPORT_AGENT);
        secondAgent = user(UserRole.SUPPORT_AGENT);
        ticketId = service.createTicket(owner, new CreateTicketRequest(TicketCategory.ORDER, "Initial question")).id();
    }

    @Test
    void createsWithRealBearerJwtAndDerivesOwnerAndSenderFromAuthentication() throws Exception {
        var result = mvc.perform(as(post("/api/tickets"), owner).content("""
                {"category":"SHIPMENT","content":"Where is it?","userId":"%s",
                 "status":"RESOLVED","priority":"URGENT","assignedTo":"%s"}
                """.formatted(other, agent)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(owner.toString()))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.priority").value("NORMAL"))
                .andExpect(jsonPath("$.assignedTo").isEmpty()).andReturn();
        var id = UUID.fromString(JsonMapper.builder().build().readTree(result.getResponse().getContentAsString()).get("id").asText());
        assertThat(result.getResponse().getHeader("Location")).isEqualTo("/api/tickets/" + id);
        assertThat(result.getRequest().getSession(false)).isNull();
        mvc.perform(as(get("/api/tickets/" + id + "/messages"), owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].senderType").value("CUSTOMER"));
    }

    @Test
    void completesHumanSupportFlowEntirelyThroughHttp() throws Exception {
        mvc.perform(as(post(base() + "/escalate"), owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ESCALATED"));
        mvc.perform(as(get("/api/support/tickets/escalations"), agent)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(ticketId.toString()));
        mvc.perform(as(post(supportBase() + "/claim"), agent).content("{\"assignedTo\":\"" + secondAgent + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.assignedTo").value(agent.toString()));
        mvc.perform(as(get("/api/support/tickets"), agent)).andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1));
        mvc.perform(as(get(base()), agent)).andExpect(status().isOk());
        mvc.perform(as(patch(supportBase() + "/priority"), agent).content("{\"priority\":\"HIGH\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.priority").value("HIGH"));
        mvc.perform(as(post(base() + "/messages"), agent).content("{\"content\":\"Please confirm\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.senderType").value("SUPPORT_AGENT"));
        mvc.perform(as(get(base() + "/messages"), agent)).andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2));
        mvc.perform(as(post(base() + "/messages"), owner).content("{\"content\":\"Confirmed\"}"))
                .andExpect(status().isCreated());
        mvc.perform(as(post(base() + "/resolve"), agent)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
        mvc.perform(as(post(base() + "/messages"), owner).content("{\"content\":\"Too late\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void paginatesFiltersAndNeverAcceptsUserIdOverride() throws Exception {
        service.createTicket(owner, new CreateTicketRequest(TicketCategory.OTHER, "Second question"));
        service.createTicket(other, new CreateTicketRequest(TicketCategory.OTHER, "Private question"));
        mvc.perform(as(get("/api/tickets").param("size", "1").param("userId", other.toString()), owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(1));
        mvc.perform(as(get("/api/tickets").param("status", "RESOLVED"), owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void messageSenderCannotBeSpoofedAndLocationIsReadable() throws Exception {
        var result = mvc.perform(as(post(base() + "/messages"), owner).content("""
                {"content":"Follow up","senderType":"AI","userId":"%s"}
                """.formatted(agent)))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.senderType").value("CUSTOMER")).andReturn();
        var location = result.getResponse().getHeader("Location");
        mvc.perform(as(get(location), owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Follow up"));
        mvc.perform(as(get(location), other)).andExpect(status().isNotFound());
    }

    @Test
    void blocksForeignCustomersAndUnassignedAgents() throws Exception {
        for (var actor : new UUID[] {other, agent}) {
            mvc.perform(as(get(base()), actor)).andExpect(status().isNotFound());
            mvc.perform(as(get(base() + "/messages"), actor)).andExpect(status().isNotFound());
            mvc.perform(as(post(base() + "/messages"), actor).content("{\"content\":\"No access\"}"))
                    .andExpect(status().isNotFound());
            mvc.perform(as(post(base() + "/resolve"), actor)).andExpect(status().isNotFound());
        }
        mvc.perform(as(post(base() + "/escalate"), other)).andExpect(status().isNotFound());
        mvc.perform(as(get("/api/support/tickets"), owner)).andExpect(status().isForbidden());
        mvc.perform(as(get("/api/support/tickets/escalations"), owner)).andExpect(status().isForbidden());
        mvc.perform(as(post(supportBase() + "/claim"), owner)).andExpect(status().isForbidden());
        mvc.perform(as(patch(supportBase() + "/priority"), owner).content("{\"priority\":\"HIGH\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(as(post("/api/tickets"), agent).content("{\"category\":\"ORDER\",\"content\":\"No\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void checksCurrentDatabaseRoleEvenIfJwtClaimsSupportRole() throws Exception {
        var misleadingRole = jwt.generateAccessToken(owner, UserRole.SUPPORT_AGENT).getTokenValue();
        mvc.perform(get("/api/support/tickets/escalations").header("Authorization", "Bearer " + misleadingRole))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsMissingAndInvalidAuthenticationOnReadsAndWrites() throws Exception {
        mvc.perform(get("/api/tickets")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/support/tickets/escalations")).andExpect(status().isUnauthorized());
        // A missing CSRF token can reject anonymous writes earlier; supply one to exercise authentication.
        mvc.perform(post("/api/tickets").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"ORDER\",\"content\":\"No identity\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/tickets").header("Authorization", "Bearer invalid.jwt.token")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @MethodSource("invalidTicketBodies")
    void validatesTicketRequest(String body) throws Exception {
        var count = tickets.count();
        mvc.perform(as(post("/api/tickets"), owner).content(body)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        assertThat(tickets.count()).isEqualTo(count);
    }

    static Stream<String> invalidTicketBodies() {
        return Stream.of("{}", "null", "{", "{\"category\":\"UNKNOWN\",\"content\":\"Question\"}",
                "{\"category\":\"ORDER\",\"content\":\" \"}",
                "{\"category\":\"ORDER\",\"content\":\"" + "a".repeat(10001) + "\"}");
    }

    @Test
    void validatesMessagePriorityIdsStatusAndPagination() throws Exception {
        mvc.perform(as(post(base() + "/messages"), owner).content("{\"content\":\" \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(as(patch(supportBase() + "/priority"), agent).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(as(patch(supportBase() + "/priority"), agent).content("{\"priority\":\"INVALID\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(as(get("/api/tickets/not-a-uuid"), owner)).andExpect(status().isBadRequest());
        mvc.perform(as(get("/api/tickets").param("status", "INVALID"), owner)).andExpect(status().isBadRequest());
        for (var path : new String[] {"/api/tickets", base() + "/messages", "/api/support/tickets/escalations"}) {
            var actor = path.startsWith("/api/support") ? agent : owner;
            mvc.perform(as(get(path).param("size", "101"), actor)).andExpect(status().isBadRequest());
            mvc.perform(as(get(path).param("sort", "secret,asc"), actor)).andExpect(status().isBadRequest());
        }
    }

    @Test
    void refusesInvalidTransitionsAndSecondClaimWithoutChangingAssignee() throws Exception {
        mvc.perform(as(post(base() + "/resolve"), owner)).andExpect(status().isConflict());
        mvc.perform(as(post(base() + "/escalate"), owner)).andExpect(status().isOk());
        mvc.perform(as(post(supportBase() + "/claim"), agent)).andExpect(status().isOk());
        mvc.perform(as(post(supportBase() + "/claim"), secondAgent)).andExpect(status().isConflict());
        assertThat(tickets.findById(ticketId).orElseThrow().getAssignedTo()).isEqualTo(agent);
        assertThat(tickets.findById(ticketId).orElseThrow().getStatus()).isEqualTo(TicketStatus.HUMAN_HANDLING);
        mvc.perform(as(patch(supportBase() + "/priority"), secondAgent).content("{\"priority\":\"LOW\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingTicketAndMessageFromDifferentTicketReturnNotFound() throws Exception {
        mvc.perform(as(get("/api/tickets/" + UUID.randomUUID() + "/messages"), owner))
                .andExpect(status().isNotFound());
        var firstMessage = messages.findByTicket_Id(ticketId, Pageable.unpaged()).getContent().getFirst().getId();
        var another = service.createTicket(owner, new CreateTicketRequest(TicketCategory.OTHER, "Other")).id();
        mvc.perform(as(get("/api/tickets/" + another + "/messages/" + firstMessage), owner))
                .andExpect(status().isNotFound());
    }

    private String base() {
        return "/api/tickets/" + ticketId;
    }

    private String supportBase() {
        return "/api/support/tickets/" + ticketId;
    }

    private UUID user(UserRole role) {
        return users.saveAndFlush(new User(UUID.randomUUID() + "@example.com", "encoded", role, "Test", "User")).getId();
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, UUID id) {
        var role = users.findById(id).orElseThrow().getRole();
        return request.header("Authorization", "Bearer " + jwt.generateAccessToken(id, role).getTokenValue())
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON);
    }
}
