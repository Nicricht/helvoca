package cl.helvoca.operations;

import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest
class BusinessAutomationPolicyIntegrationTest {

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
    @Autowired BusinessAutomationPolicyRepository policies;
    @Autowired JdbcTemplate jdbc;

    @Test
    void persistsSparseTenantOverride() {
        Business business = new Business();
        business.setName("Automation Policy Test");
        business = businesses.saveAndFlush(business);

        BusinessAutomationPolicy policy = new BusinessAutomationPolicy();
        policy.setBusinessId(business.getId());
        policy.setOperationType(BusinessOperation.Type.QUOTE);
        policy.setAutoExecute(false);
        policy.setCustomerConfirmation(BusinessAutomationPolicy.CustomerConfirmation.NONE);
        policy.setPaymentRequirement(BusinessAutomationPolicy.PaymentRequirement.NONE);
        policy.setRetryPolicy(BusinessAutomationPolicy.RetryPolicy.SAFE_AUTOMATIC);
        policy.setMaxAutoRetries(3);
        policy.setEscalationPolicy(BusinessAutomationPolicy.EscalationPolicy.ONLY_IF_UNRESOLVABLE);
        policies.saveAndFlush(policy);

        BusinessAutomationPolicy stored = policies
                .findByBusinessIdAndOperationType(business.getId(), BusinessOperation.Type.QUOTE)
                .orElseThrow();
        assertFalse(stored.isAutoExecute());
        assertEquals(3, stored.getMaxAutoRetries());
    }

    @Test
    void databaseRejectsRemovingTransactionalCustomerConfirmation() {
        Business business = new Business();
        business.setName("Confirmation Floor Test");
        business = businesses.saveAndFlush(business);

        UUID businessId = business.getId();
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO business_automation_policy(
                    business_id, operation_type, auto_execute, customer_confirmation,
                    payment_requirement, retry_policy, max_auto_retries, escalation_policy)
                VALUES (?, 'ORDER', TRUE, 'NONE', 'NONE', 'SAFE_AUTOMATIC', 2, 'ONLY_IF_UNRESOLVABLE')
                """, businessId));
    }

    @Test
    void databaseRejectsRecursivePaymentRequirement() {
        Business business = new Business();
        business.setName("Payment Recursion Test");
        business = businesses.saveAndFlush(business);

        UUID businessId = business.getId();
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO business_automation_policy(
                    business_id, operation_type, auto_execute, customer_confirmation,
                    payment_requirement, retry_policy, max_auto_retries, escalation_policy)
                VALUES (?, 'PAYMENT', TRUE, 'EXPLICIT', 'REQUIRED_AFTER_CONFIRMATION',
                        'SAFE_AUTOMATIC', 2, 'ONLY_IF_UNRESOLVABLE')
                """, businessId));
    }

    @Test
    void databaseRejectsInconsistentRetryConfiguration() {
        Business business = new Business();
        business.setName("Retry Constraint Test");
        business = businesses.saveAndFlush(business);

        UUID businessId = business.getId();
        assertThrows(DataAccessException.class, () -> jdbc.update("""
                INSERT INTO business_automation_policy(
                    business_id, operation_type, auto_execute, customer_confirmation,
                    payment_requirement, retry_policy, max_auto_retries, escalation_policy)
                VALUES (?, 'LEAD', TRUE, 'NONE', 'NONE', 'NONE', 2, 'ONLY_IF_UNRESOLVABLE')
                """, businessId));
    }
}
