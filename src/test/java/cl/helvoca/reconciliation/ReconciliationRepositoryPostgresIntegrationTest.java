package cl.helvoca.reconciliation;

import cl.helvoca.security.TenantDatabaseContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest
class ReconciliationRepositoryPostgresIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("app.seed.enabled", () -> "false");
    }

    @Autowired ReconciliationRepository repository;
    @Autowired TenantDatabaseContext databaseContext;
    @Autowired @Qualifier("migrationDataSource") DataSource migrationDataSource;

    JdbcTemplate ownerJdbc;
    UUID businessA;
    UUID businessB;
    Instant now;

    @BeforeEach
    void setUp() {
        ownerJdbc = new JdbcTemplate(migrationDataSource);
        businessA = UUID.randomUUID();
        businessB = UUID.randomUUID();
        now = Instant.parse("2026-09-27T18:00:00Z");

        ownerJdbc.update("INSERT INTO business(id, name) VALUES (?, ?)", businessA, "Reconciliation A " + businessA);
        ownerJdbc.update("INSERT INTO business(id, name) VALUES (?, ?)", businessB, "Reconciliation B " + businessB);
    }

    @Test
    void detectsSafeAnomaliesAndNeverLeaksAnotherTenant() {
        UUID productA = catalogItem(businessA, "Product A");
        UUID productB = catalogItem(businessB, "Product B");

        UUID paidOrderA = operation(businessA, "ORDER", "CONFIRMED");
        UUID paidPaymentOperationA = operation(businessA, "PAYMENT", "CONFIRMED");
        UUID paidPaymentA = payment(
                businessA, paidPaymentOperationA, paidOrderA, "SUCCEEDED", "paid-a");
        reservation(businessA, productA, paidOrderA);

        UUID terminalOrderA = operation(businessA, "ORDER", "CONFIRMED");
        UUID terminalPaymentOperationA = operation(businessA, "PAYMENT", "CONFIRMED");
        UUID terminalPaymentA = payment(
                businessA, terminalPaymentOperationA, terminalOrderA, "EXPIRED", "expired-a");
        reservation(businessA, productA, terminalOrderA);

        UUID orphanReference = UUID.randomUUID();
        UUID orphanReservation = reservation(businessA, productA, orphanReference);

        UUID paidOrderB = operation(businessB, "ORDER", "CONFIRMED");
        UUID paidPaymentOperationB = operation(businessB, "PAYMENT", "CONFIRMED");
        payment(businessB, paidPaymentOperationB, paidOrderB, "SUCCEEDED", "paid-b");
        reservation(businessB, productB, paidOrderB);

        List<ReconciliationAnomaly> anomalies = databaseContext.callAsTenant(
                businessA, () -> repository.detect(businessA, now));

        assertTrue(anomalies.stream().anyMatch(value ->
                value.type() == ReconciliationAnomaly.Type.PAYMENT_SUCCEEDED_INVENTORY_RESERVED
                        && paidPaymentA.equals(value.subjectId())
                        && paidOrderA.equals(value.operationId())
                        && value.safeRepairAvailable()));

        assertTrue(anomalies.stream().anyMatch(value ->
                value.type() == ReconciliationAnomaly.Type.PAYMENT_TERMINAL_INVENTORY_RESERVED
                        && terminalPaymentA.equals(value.subjectId())
                        && terminalOrderA.equals(value.operationId())
                        && value.safeRepairAvailable()));

        assertTrue(anomalies.stream().anyMatch(value ->
                value.type() == ReconciliationAnomaly.Type.ORPHAN_RESERVATION
                        && orphanReservation.equals(value.subjectId())
                        && orphanReference.equals(value.operationId())
                        && value.safeRepairAvailable()));

        assertFalse(anomalies.stream().anyMatch(value ->
                paidOrderB.equals(value.operationId()) || paidPaymentOperationB.equals(value.operationId())));
    }

    @Test
    void detectsRecommendationOnlyWebhookAndExpiredLeaseWithoutMutatingThem() {
        UUID webhookId = UUID.randomUUID();
        ownerJdbc.update("""
                INSERT INTO payment_webhook_event(
                    id, business_id, provider, event_id, external_id, status,
                    received_at, processed_at
                ) VALUES (?, ?, 'sandbox', ?, ?, 'FAILED', ?, ?)
                """,
                webhookId, businessA, "event-" + webhookId, "external-" + webhookId,
                Timestamp.from(now.minusSeconds(600)), Timestamp.from(now.minusSeconds(300)));

        UUID jobId = UUID.randomUUID();
        ownerJdbc.update("""
                INSERT INTO persistent_job(
                    id, business_id, operation_id, job_type, status, idempotency_key,
                    payload, attempt_count, max_attempts, next_attempt_at,
                    lease_owner, lease_expires_at, created_at, updated_at
                ) VALUES (?, ?, NULL, 'OUTBOUND_MESSAGE_DISPATCH', 'RUNNING', ?,
                    '{}'::jsonb, 1, 5, ?, 'worker-v6', ?, ?, ?)
                """,
                jobId, businessA, "v6-job-" + jobId,
                Timestamp.from(now.minusSeconds(600)),
                Timestamp.from(now.minusSeconds(60)),
                Timestamp.from(now.minusSeconds(900)),
                Timestamp.from(now.minusSeconds(60)));

        List<ReconciliationAnomaly> anomalies = databaseContext.callAsTenant(
                businessA, () -> repository.detect(businessA, now));

        ReconciliationAnomaly webhook = anomalies.stream()
                .filter(value -> value.type() == ReconciliationAnomaly.Type.RECOVERABLE_WEBHOOK_FAILED)
                .findFirst().orElseThrow();
        assertEquals(webhookId, webhook.subjectId());
        assertFalse(webhook.safeRepairAvailable());

        ReconciliationAnomaly job = anomalies.stream()
                .filter(value -> value.type() == ReconciliationAnomaly.Type.JOB_STUCK)
                .findFirst().orElseThrow();
        assertEquals(jobId, job.subjectId());
        assertFalse(job.safeRepairAvailable());

        assertEquals("FAILED", ownerJdbc.queryForObject(
                "SELECT status FROM payment_webhook_event WHERE id = ?",
                String.class, webhookId));
        assertEquals("RUNNING", ownerJdbc.queryForObject(
                "SELECT status FROM persistent_job WHERE id = ?",
                String.class, jobId));
    }

    @Test
    void repairClaimIsDurableTenantScopedAndIdempotent() {
        UUID subjectId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        ReconciliationAnomaly anomaly = new ReconciliationAnomaly(
                ReconciliationAnomaly.Type.ORPHAN_RESERVATION,
                ReconciliationAnomaly.Severity.HIGH,
                "INVENTORY_RESERVATION",
                subjectId,
                operationId,
                now,
                true,
                "release",
                Map.of("reason", "orphan"));

        String key = "reconciliation:test:" + subjectId;
        ReconciliationRepository.ClaimResult first = databaseContext.callAsTenant(
                businessA, () -> repository.claimRepair(businessA, anomaly, key));
        assertEquals(ReconciliationRepository.ClaimState.CLAIMED, first.state());

        databaseContext.runAsTenant(
                businessA, () -> repository.complete(businessA, first.actionId()));

        ReconciliationRepository.ClaimResult replay = databaseContext.callAsTenant(
                businessA, () -> repository.claimRepair(businessA, anomaly, key));
        assertEquals(ReconciliationRepository.ClaimState.ALREADY_COMPLETED, replay.state());
        assertEquals(first.actionId(), replay.actionId());

        assertEquals(1, ownerJdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM reconciliation_action
                 WHERE business_id = ? AND idempotency_key = ?
                """, Integer.class, businessA, key));

        assertEquals(0, ownerJdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM reconciliation_action
                 WHERE business_id = ?
                """, Integer.class, businessB));
    }

    private UUID catalogItem(UUID businessId, String name) {
        UUID id = UUID.randomUUID();
        ownerJdbc.update("""
                INSERT INTO catalog_item(
                    id, business_id, kind, name, currency, active, created_at, updated_at
                ) VALUES (?, ?, 'PRODUCT', ?, 'CLP', TRUE, NOW(), NOW())
                """, id, businessId, name + " " + id);
        return id;
    }

    private UUID operation(UUID businessId, String type, String status) {
        UUID id = UUID.randomUUID();
        ownerJdbc.update("""
                INSERT INTO business_operation(
                    id, business_id, type, status, source, revision, currency,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'API', 1, 'CLP', NOW(), NOW())
                """, id, businessId, type, status);
        return id;
    }

    private UUID payment(
            UUID businessId,
            UUID paymentOperationId,
            UUID targetOperationId,
            String status,
            String suffix) {
        UUID id = UUID.randomUUID();
        ownerJdbc.update("""
                INSERT INTO business_payment(
                    id, operation_id, business_id, target_operation_id, provider,
                    idempotency_key, amount, currency, status, source,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'sandbox', ?, ?, 'CLP', ?, 'API', NOW(), NOW())
                """,
                id, paymentOperationId, businessId, targetOperationId,
                "v6-" + suffix + "-" + id, new BigDecimal("1000"), status);
        return id;
    }

    private UUID reservation(UUID businessId, UUID catalogItemId, UUID orderOperationId) {
        UUID id = UUID.randomUUID();
        ownerJdbc.update("""
                INSERT INTO inventory_reservation(
                    id, business_id, catalog_item_id, quantity, status,
                    reference_type, reference_id, expires_at, created_at, updated_at
                ) VALUES (?, ?, ?, 1, 'ACTIVE', 'ORDER_OPERATION', ?, ?, NOW(), NOW())
                """,
                id, businessId, catalogItemId, orderOperationId,
                Timestamp.from(now.plusSeconds(3600)));
        return id;
    }
}
