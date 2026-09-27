package cl.helvoca.bootstrap;

import cl.helvoca.agent.AiAgent;
import cl.helvoca.agent.AiAgentRepository;
import cl.helvoca.agent.AiCapability;
import cl.helvoca.billing.BusinessSubscription;
import cl.helvoca.billing.BusinessSubscriptionRepository;
import cl.helvoca.billing.SubscriptionStatus;
import cl.helvoca.booking.Booking;
import cl.helvoca.booking.BookingRepository;
import cl.helvoca.booking.BookingSource;
import cl.helvoca.booking.BookingStatus;
import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessProfile;
import cl.helvoca.business.BusinessProfileRepository;
import cl.helvoca.catalog.CatalogItem;
import cl.helvoca.catalog.CatalogItemRepository;
import cl.helvoca.customer.Customer;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.knowledge.KnowledgeItemRepository;
import cl.helvoca.operations.BusinessOperation;
import cl.helvoca.operations.BusinessOperationRepository;
import cl.helvoca.schedule.BusinessHourRepository;
import cl.helvoca.servicecatalog.ServiceItem;
import cl.helvoca.servicecatalog.ServiceItemRepository;
import cl.helvoca.user.AppUser;
import cl.helvoca.user.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest
@Transactional
class DevDataInitializerPostgresIntegrationTest {

    private static final String ADMIN_EMAIL = "demo-postgres@helvoca.local";

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

        registry.add("app.seed.enabled", () -> "true");
        registry.add("app.seed.admin-email", () -> ADMIN_EMAIL);
        registry.add("app.seed.admin-password", () -> "local-demo-certification-only");

        registry.add("app.twilio.provisioning-enabled", () -> "false");
        registry.add("app.whatsapp.enabled", () -> "false");
        registry.add("app.meta.whatsapp.enabled", () -> "false");
        registry.add("app.outbound.delivery-enabled", () -> "false");
        registry.add("app.mercadopago.enabled", () -> "false");
        registry.add("app.gemini.live.enabled", () -> "false");
        registry.add("app.openai.live.enabled", () -> "false");
    }

    @Autowired DevDataInitializer initializer;
    @Autowired AppUserRepository users;
    @Autowired BusinessProfileRepository profiles;
    @Autowired ServiceItemRepository services;
    @Autowired CatalogItemRepository catalog;
    @Autowired BusinessHourRepository hours;
    @Autowired KnowledgeItemRepository knowledge;
    @Autowired AiAgentRepository agents;
    @Autowired CustomerRepository customers;
    @Autowired BookingRepository bookings;
    @Autowired BusinessOperationRepository operations;
    @Autowired BusinessSubscriptionRepository subscriptions;

    @Test
    void commercialDemoSeedPersistsCompleteSafeAndIdempotentFixtureInPostgres() {
        AppUser admin = users.findByEmailIgnoreCase(ADMIN_EMAIL).orElseThrow();
        Business business = admin.getBusiness();

        assertNotNull(business);
        assertEquals("Barbería Norte Demo", business.getName());
        assertEquals("America/Santiago", business.getTimezone());
        assertEquals("es", business.getLanguage());
        assertNull(business.getHumanTransferPhone());

        BusinessProfile profile = profiles.findById(business.getId()).orElseThrow();
        assertEquals("barbershop", profile.getPresetKey());
        assertTrue(profile.getPublicDescription().contains("ficticia"));
        assertEquals("Pasaje Demo 123", profile.getAddressLine());
        assertEquals("Providencia", profile.getCommune());
        assertEquals("Santiago", profile.getCity());
        assertEquals("CL", profile.getCountryCode());
        assertEquals("CLP", profile.getDefaultCurrency());
        assertEquals(Boolean.TRUE, profile.getSellsProducts());
        assertEquals(Boolean.TRUE, profile.getSellsServices());
        assertEquals(Boolean.TRUE, profile.getUsesReservations());
        assertNull(profile.getPublicPhone());
        assertTrue(profile.getPublicEmail().endsWith(".invalid"));
        assertTrue(profile.getWebsiteUrl().endsWith(".invalid"));

        List<ServiceItem> activeServices = services.findAllByBusinessIdOrderByNameAsc(business.getId()).stream()
                .filter(ServiceItem::isActive)
                .toList();
        assertEquals(6, activeServices.size());
        assertService(activeServices, "Corte clásico", 45, "15990");
        assertService(activeServices, "Corte + barba", 60, "22990");
        assertService(activeServices, "Perfilado de barba", 30, "11990");
        assertService(activeServices, "Fade premium", 60, "19990");
        assertService(activeServices, "Corte infantil", 40, "13990");
        assertService(activeServices, "Tratamiento capilar", 30, "12990");

        List<CatalogItem> activeProducts = catalog.findAllByBusinessIdAndActiveTrueOrderByNameAsc(business.getId()).stream()
                .filter(item -> item.getKind() == CatalogItem.Kind.PRODUCT)
                .toList();
        assertEquals(2, activeProducts.size());
        assertTrue(activeProducts.stream().allMatch(item -> "CLP".equals(item.getCurrency())));

        assertEquals(6, hours.countByBusinessId(business.getId()));
        assertEquals(6, knowledge.findAllByBusinessIdAndActiveTrueOrderByTitleAsc(business.getId()).size());

        AiAgent agent = agents.findByBusinessId(business.getId()).orElseThrow();
        assertTrue(agent.isActive());
        assertEquals("RecepVoz Demo", agent.getName());
        assertEquals("es", agent.getLanguage());
        assertNull(agent.getVoice());
        assertTrue(agent.getCapabilities().containsAll(Set.of(
                AiCapability.GET_BUSINESS_INFORMATION,
                AiCapability.LIST_SERVICES,
                AiCapability.SEARCH_KNOWLEDGE,
                AiCapability.CHECK_BOOKING_AVAILABILITY,
                AiCapability.CREATE_BOOKING
        )));

        List<Customer> demoCustomers = customers.findAllByBusinessIdOrderByCreatedAtDesc(business.getId());
        assertEquals(3, demoCustomers.size());
        assertTrue(demoCustomers.stream().allMatch(customer ->
                customer.getEmail() != null
                        && customer.getEmail().endsWith(".invalid")
                        && customer.getPhone() == null));

        List<Booking> demoBookings = bookings.findAllByBusinessIdOrderByStartAtDesc(business.getId());
        assertEquals(3, demoBookings.size());
        Instant now = Instant.now();
        for (Booking booking : demoBookings) {
            assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
            assertEquals(BookingSource.ADMIN, booking.getSource());
            assertTrue(booking.getStartAt().isAfter(now));
            assertTrue(booking.getEndAt().isAfter(booking.getStartAt()));
            assertTrue(booking.getNotes().startsWith("DEMO_FIXTURE:"));
            assertNotNull(booking.getOperationId());

            BusinessOperation operation = operations.findByIdAndBusinessId(
                    booking.getOperationId(), business.getId()).orElseThrow();
            assertEquals(BusinessOperation.Type.BOOKING, operation.getType());
            assertEquals(BusinessOperation.Status.CONFIRMED, operation.getStatus());
            assertEquals(booking.getCustomerId(), operation.getCustomerId());
            assertEquals(booking.getId().toString(), String.valueOf(operation.getMetadata().get("bookingId")));
        }

        BusinessSubscription subscription = subscriptions.findByBusinessId(business.getId()).orElseThrow();
        assertEquals("BASIC", subscription.getPlanCode());
        assertEquals(SubscriptionStatus.TRIALING, subscription.getStatus());
        assertNull(subscription.getExternalCustomerId());
        assertNull(subscription.getExternalSubscriptionId());
        assertNull(subscription.getBillingProvider());
        assertNull(subscription.getBillingCheckoutUrl());

        long serviceCount = services.count();
        long productCount = catalog.count();
        long hourCount = hours.count();
        long knowledgeCount = knowledge.count();
        long customerCount = customers.count();
        long bookingCount = bookings.count();
        long operationCount = operations.count();
        long subscriptionCount = subscriptions.count();

        initializer.run();

        assertEquals(serviceCount, services.count());
        assertEquals(productCount, catalog.count());
        assertEquals(hourCount, hours.count());
        assertEquals(knowledgeCount, knowledge.count());
        assertEquals(customerCount, customers.count());
        assertEquals(bookingCount, bookings.count());
        assertEquals(operationCount, operations.count());
        assertEquals(subscriptionCount, subscriptions.count());

        Set<String> fixtureMarkers = bookings.findAllByBusinessIdOrderByStartAtDesc(business.getId()).stream()
                .map(Booking::getNotes)
                .collect(Collectors.toSet());
        assertEquals(Set.of(
                "DEMO_FIXTURE:matias-corte-clasico",
                "DEMO_FIXTURE:camila-perfilado-barba",
                "DEMO_FIXTURE:javiera-fade-premium"
        ), fixtureMarkers);
        assertFalse(fixtureMarkers.stream().anyMatch(marker -> marker == null || marker.isBlank()));
    }

    private static void assertService(List<ServiceItem> services,
                                      String name,
                                      int durationMinutes,
                                      String expectedPrice) {
        ServiceItem service = services.stream()
                .filter(item -> name.equals(item.getName()))
                .findFirst()
                .orElseThrow();

        assertEquals(durationMinutes, service.getDurationMinutes());
        assertEquals(0, service.getPrice().compareTo(new BigDecimal(expectedPrice)));
    }
}
