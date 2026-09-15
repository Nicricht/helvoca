package cl.helvoca.operations;

import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
@SpringBootTest
class HumanHandoffInvalidLifecycleIntegrationTest {

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

    @Autowired HumanHandoffService handoffs;
    @Autowired BusinessRepository businesses;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void terminalHandoffRejectsInvalidLifecycleTransitions() {
        Business business = new Business();
        business.setName("Invalid Handoff Lifecycle");
        business = businesses.saveAndFlush(business);

        HumanHandoffService.Creation created = handoffs.createForUnresolvable(
                business.getId(),
                BusinessOperation.Type.REQUEST,
                UUID.randomUUID(),
                null,
                "create_request",
                "UNEXPECTED_OPERATION_FAILURE",
                0);

        authenticate(business.getId());
        HumanHandoffService.HandoffView resolved = handoffs.resolve(
                created.handoffId(), "admin@negocio.cl");
        assertEquals(HumanHandoffService.Status.RESOLVED, resolved.status());

        assertThrows(IllegalStateException.class,
                () -> handoffs.acknowledge(created.handoffId(), "admin@negocio.cl"));
        assertThrows(IllegalStateException.class,
                () -> handoffs.assign(created.handoffId(), "operador@negocio.cl", "admin@negocio.cl"));
        assertThrows(IllegalStateException.class,
                () -> handoffs.cancel(created.handoffId(), "admin@negocio.cl"));

        assertEquals(List.of("CREATED", "RESOLVED"),
                handoffs.history(created.handoffId()).stream()
                        .map(HumanHandoffService.EventView::eventType)
                        .toList());
    }

    private static void authenticate(UUID businessId) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject("test-user")
                .claim("business_id", businessId.toString())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority("ROLE_BUSINESS_ADMIN"))));
    }
}
