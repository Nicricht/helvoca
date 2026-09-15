package cl.helvoca.omnichannel;

import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.customer.Customer;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.operations.BusinessOrder;
import cl.helvoca.operations.ConversationOperationState;
import cl.helvoca.operations.ConversationOperationStateRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
@SpringBootTest
@Transactional
class OmnichannelStateUniquenessIntegrationTest {

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
    @Autowired CustomerRepository customers;
    @Autowired OmnichannelSessionRepository sessions;
    @Autowired ConversationOperationStateRepository states;

    @Test
    void databaseRejectsTwoSharedStatesForSameTenantSession() {
        Business business = new Business();
        business.setName("Uniqueness tenant");
        business = businesses.saveAndFlush(business);

        Customer customer = new Customer();
        customer.setBusinessId(business.getId());
        customer.setName("Customer");
        customer = customers.saveAndFlush(customer);

        OmnichannelSession session = new OmnichannelSession();
        session.setBusinessId(business.getId());
        session.setCustomerId(customer.getId());
        session.setStatus(OmnichannelSession.Status.ACTIVE);
        session.setOpenedAt(Instant.now());
        session.setLastActivityAt(Instant.now());
        session = sessions.saveAndFlush(session);

        ConversationOperationState voice = state(
                business.getId(), session.getId(), BusinessOrder.Source.VOICE);
        states.saveAndFlush(voice);

        ConversationOperationState whatsapp = state(
                business.getId(), session.getId(), BusinessOrder.Source.WHATSAPP);
        assertThrows(DataIntegrityViolationException.class, () -> states.saveAndFlush(whatsapp));
    }

    private static ConversationOperationState state(UUID businessId,
                                                    UUID omnichannelSessionId,
                                                    BusinessOrder.Source channel) {
        ConversationOperationState state = new ConversationOperationState();
        state.setBusinessId(businessId);
        state.setSourceReferenceId(UUID.randomUUID());
        state.setChannel(channel);
        state.setOmnichannelSessionId(omnichannelSessionId);
        state.setRevision(1);
        state.setState(new LinkedHashMap<>());
        return state;
    }
}
