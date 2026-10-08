package cl.helvoca.request;

import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.operations.BusinessOperation;
import cl.helvoca.operations.HumanAttentionService;
import cl.helvoca.operations.HumanHandoffService;
import cl.helvoca.testsupport.ExplicitSystemDatabaseScopeSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest
class RequestLifecycleAttentionIntegrationTest extends ExplicitSystemDatabaseScopeSupport {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("app.seed.enabled", () -> "false");
    }

    @Autowired BusinessRepository businesses;
    @Autowired BusinessRequestService requests;
    @Autowired RequestLifecycleEventService events;
    @Autowired HumanAttentionService attention;
    @Autowired HumanHandoffService handoffs;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void humanStateChangesAreLockedAuditedAndTerminalStatusesCannotReopen() {
        Business business = business("Request state history");
        authenticate(business.getId());
        var request = requests.create(new BusinessRequestDtos.Create(
                "GENERAL", "Consulta especial", null, null, null, RequestPriority.NORMAL, null));

        assertEquals(1, attention.pending().stream().filter(x ->
                x.kind() == HumanAttentionService.Kind.REQUEST && x.id().equals(request.id())).count());

        requests.setStatus(request.id(), RequestStatus.IN_PROGRESS);
        requests.setStatus(request.id(), RequestStatus.IN_PROGRESS); // no duplicate event
        requests.setStatus(request.id(), RequestStatus.RESOLVED);
        requests.setStatus(request.id(), RequestStatus.RESOLVED); // idempotent
        var history = events.history(request.id());
        assertEquals(2, history.size());
        assertEquals(List.of(RequestStatus.IN_PROGRESS, RequestStatus.RESOLVED),
                history.stream().map(RequestLifecycleEventService.Transition::status).toList());
        assertTrue(history.stream().allMatch(event ->
                event.actorType().equals("BUSINESS_USER") && event.evidenceEventId() == null));

        assertThrows(IllegalArgumentException.class,
                () -> requests.setStatus(request.id(), RequestStatus.OPEN));
        assertTrue(attention.pending().stream().noneMatch(x -> request.id().equals(x.id())));
        assertEquals(2, events.history(request.id()).size());
    }

    @Test
    void activeHandoffReplacesOpenRequestInsteadOfCreatingTwoInboxItems() {
        Business business = business("One attention item");
        authenticate(business.getId());
        var request = requests.create(new BusinessRequestDtos.Create(
                "GENERAL", "Necesita un humano", null, null, null, RequestPriority.HIGH, null));
        UUID operationId = jdbc.queryForObject(
                "SELECT operation_id FROM business_request WHERE id = ? AND business_id = ?",
                UUID.class, request.id(), business.getId());

        var first = handoffs.createForUnresolvable(
                business.getId(), BusinessOperation.Type.REQUEST, null, operationId,
                "create_request", "CUSTOMER_NEEDS_SUPPORT", 0);
        var repeated = handoffs.createForUnresolvable(
                business.getId(), BusinessOperation.Type.REQUEST, null, operationId,
                "create_request", "CUSTOMER_NEEDS_SUPPORT", 0);
        assertEquals(first.handoffId(), repeated.handoffId());
        assertFalse(repeated.created());

        var entries = attention.pending().stream()
                .filter(x -> operationId.equals(x.operationId())).toList();
        assertEquals(1, entries.size());
        assertEquals(HumanAttentionService.Kind.HANDOFF, entries.getFirst().kind());

        handoffs.resolve(first.handoffId(), "tester");
        // A resolved handoff alone is not proof of request resolution.
        var afterHandoff = attention.pending().stream()
                .filter(x -> operationId.equals(x.operationId())).toList();
        assertEquals(1, afterHandoff.size());
        assertEquals(HumanAttentionService.Kind.REQUEST, afterHandoff.getFirst().kind());
    }

    @Test
    void anotherTenantCannotSeeOrChangeRequestOrItsHistory() {
        Business a = business("Scoped request A");
        Business b = business("Scoped request B");
        authenticate(a.getId());
        var request = requests.create(new BusinessRequestDtos.Create(
                "GENERAL", "Solo negocio A", null, null, null, RequestPriority.NORMAL, null));
        requests.setStatus(request.id(), RequestStatus.IN_PROGRESS);
        assertEquals(1, events.history(request.id()).size());

        authenticate(b.getId());
        assertThrows(cl.helvoca.common.NotFoundException.class,
                () -> requests.setStatus(request.id(), RequestStatus.RESOLVED));
        assertTrue(events.history(request.id()).isEmpty());
        assertTrue(attention.pending().stream()
                .noneMatch(item -> item.id().equals(request.id())));
    }

    private Business business(String name) {
        Business business = new Business();
        business.setName(name);
        return businesses.saveAndFlush(business);
    }

    private static void authenticate(UUID businessId) {
        Jwt jwt = Jwt.withTokenValue("request-test")
                .header("alg", "none")
                .subject(UUID.randomUUID().toString())
                .claim("business_id", businessId.toString())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority("ROLE_BUSINESS_ADMIN"))));
    }
}
