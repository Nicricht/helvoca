package cl.helvoca.telephony;

import cl.helvoca.testsupport.ExplicitSystemDatabaseScopeSupport;
import cl.helvoca.billing.BusinessSubscription;
import cl.helvoca.billing.BusinessSubscriptionRepository;
import cl.helvoca.billing.SubscriptionStatus;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
@SpringBootTest
class CallDailyBudgetIntegrationTest extends ExplicitSystemDatabaseScopeSupport {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("app.seed.enabled", () -> "false");
        registry.add("app.commercial.calls.max-estimated-cost-per-business-per-utc-day-usd", () -> "0.50");
        registry.add("app.commercial.calls.estimated-new-call-reservation-usd", () -> "0.20");
        registry.add("app.commercial.calls.telephony-cost-per-minute-usd", () -> "0.10");
        // The provider-specific Twilio map overrides the generic rate.
        registry.add("app.commercial.calls.telephony-provider-cost-per-minute-usd.twilio", () -> "0.10");
    }

    @Autowired CallLifecycleService lifecycle;
    @Autowired BusinessRepository businesses;
    @Autowired PhoneNumberRepository phoneNumbers;
    @Autowired BusinessSubscriptionRepository subscriptions;
    @Autowired CallSessionRepository calls;

    @Test
    void postgresBudgetIsTenantIsolatedIncludesUnknownCostsAndKeepsRetriesIdempotent() {
        Fixture first = tenant("Budget First", "+14355551911");
        Fixture second = tenant("Budget Second", "+14355551912");
        UUID firstCall = admit(first, "CA-budget-first-1");
        assertNotNull(firstCall);
        assertEquals(firstCall, admit(first, "CA-budget-first-1")); // no second reservation
        lifecycle.updateStatus("CA-budget-first-1", "completed", 0);
        UUID secondCall = admit(first, "CA-budget-first-2");
        assertNotNull(secondCall);
        lifecycle.updateStatus("CA-budget-first-2", "completed", 0);

        CallCapacityExceededException denied = assertThrows(CallCapacityExceededException.class,
                () -> admit(first, "CA-budget-first-3"));
        assertEquals("Estimated daily call budget reached for business", denied.getMessage());
        // A different tenant has its own budget and cannot be blocked by A.
        assertNotNull(admit(second, "CA-budget-second-1"));
        assertEquals(firstCall, admit(first, "CA-budget-first-1"));
        assertEquals(2L, calls.countByBusinessIdAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                first.businessId(), Instant.now().truncatedTo(ChronoUnit.DAYS),
                Instant.now().truncatedTo(ChronoUnit.DAYS).plus(1, ChronoUnit.DAYS)));
    }

    @Test
    void postgresUsesLargerCompletedEstimateRatherThanOnlyReservationFloor() {
        Fixture fixture = tenant("Budget Higher Charge", "+14355551913");
        admit(fixture, "CA-budget-higher-1");
        // 180 s x USD 0.10/min = USD 0.30 > reservation USD 0.20
        lifecycle.updateStatus("CA-budget-higher-1", "completed", 180);
        assertEquals(0, calls.findByProviderCallId("CA-budget-higher-1")
                .orElseThrow().getEstimatedTotalCostUsd()
                .compareTo(new java.math.BigDecimal("0.30")));
        admit(fixture, "CA-budget-higher-2"); // 0.30 + 0.20 exactly ceiling 0.50
        // Complete this call so the next rejection CANNOT come from concurrency.
        lifecycle.updateStatus("CA-budget-higher-2", "completed", 0);
        CallCapacityExceededException denied = assertThrows(CallCapacityExceededException.class,
                () -> admit(fixture, "CA-budget-higher-3"));
        assertEquals("Estimated daily call budget reached for business", denied.getMessage());
    }

    private UUID admit(Fixture tenant, String providerCallSid) {
        return databaseContext.callAsTenant(tenant.businessId(), () ->
                lifecycle.startInboundCall("twilio", providerCallSid,
                        "+56910001911", tenant.phone()));
    }

    private Fixture tenant(String name, String number) {
        Business business = new Business();
        business.setName(name);
        business.setTimezone("America/Santiago");
        business.setLanguage("es");
        business = businesses.saveAndFlush(business);

        BusinessSubscription subscription = new BusinessSubscription();
        subscription.setBusinessId(business.getId());
        subscription.setPlanCode("BASIC");
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setCurrentPeriodStart(Instant.now().minus(1, ChronoUnit.DAYS));
        subscription.setCurrentPeriodEnd(Instant.now().plus(30, ChronoUnit.DAYS));
        subscriptions.saveAndFlush(subscription);

        PhoneNumber phone = new PhoneNumber();
        phone.setBusinessId(business.getId());
        phone.setPhoneNumber(number);
        phone.setProvider("TWILIO");
        phone.setActive(true);
        phoneNumbers.saveAndFlush(phone);
        return new Fixture(business.getId(), number);
    }

    private record Fixture(UUID businessId, String phone) { }
}
