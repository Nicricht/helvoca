package cl.helvoca.schedule;

import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.security.TenantDatabaseContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest
class BusinessHoursSelectedIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("app.seed.enabled", () -> "false");
    }

    @Autowired BusinessRepository businesses;
    @Autowired BusinessHourRepository hours;
    @Autowired BusinessHoursAdminService service;
    @Autowired TenantDatabaseContext context;

    private UUID business() {
        return context.callAsSystem(() -> {
            Business business = new Business();
            business.setName("Selected hours integration " + UUID.randomUUID());
            return businesses.saveAndFlush(business).getId();
        });
    }

    private static BusinessHoursRequest.Interval hour(int day, int open, int close) {
        return new BusinessHoursRequest.Interval(day, LocalTime.of(open, 0), LocalTime.of(close, 0));
    }

    private <T> T asTenant(UUID businessId, Supplier<T> work) {
        Jwt jwt = Jwt.withTokenValue("hours-test")
                .header("alg", "none")
                .claim("sub", "hours-admin")
                .claim("business_id", businessId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        AbstractAuthenticationToken previous = SecurityContextHolder.getContext().getAuthentication()
                instanceof AbstractAuthenticationToken a ? a : null;
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        try {
            return context.callAsTenant(businessId, work);
        } finally {
            SecurityContextHolder.getContext().setAuthentication(previous);
        }
    }

    @Test
    void differentAdministratorsPatchingDifferentDaysPreserveEachOthersWork() throws Exception {
        UUID tenant = business();
        asTenant(tenant, () -> service.replace(new BusinessHoursRequest(List.of(hour(2, 10, 19)))));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch fire = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            Future<List<BusinessHourResponse>> first = workers.submit(() -> {
                ready.countDown();
                assertTrue(fire.await(15, TimeUnit.SECONDS));
                return asTenant(tenant, () -> service.mergeSelected(new BusinessHoursSelectedRequest(
                        List.of(hour(1, 9, 18)), List.of())));
            });
            Future<List<BusinessHourResponse>> second = workers.submit(() -> {
                ready.countDown();
                assertTrue(fire.await(15, TimeUnit.SECONDS));
                return asTenant(tenant, () -> service.mergeSelected(new BusinessHoursSelectedRequest(
                        List.of(hour(3, 11, 20)), List.of())));
            });
            assertTrue(ready.await(15, TimeUnit.SECONDS));
            fire.countDown();
            first.get(40, TimeUnit.SECONDS);
            second.get(40, TimeUnit.SECONDS);
        }
        var actual = asTenant(tenant, service::list);
        assertEquals(List.of(1, 2, 3), actual.stream().map(BusinessHourResponse::dayOfWeek).toList());
        assertEquals(LocalTime.of(10, 0), actual.get(1).openTime());
        assertEquals(LocalTime.of(11, 0), actual.get(2).openTime());
    }

    @Test
    void sameDayStaleEditIsRejectedAndOtherTenantNeverChanges() throws Exception {
        UUID tenantA = business();
        UUID tenantB = business();
        asTenant(tenantB, () -> service.replace(new BusinessHoursRequest(List.of(hour(1, 7, 15)))));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch fire = new CountDownLatch(1);
        int successes = 0;
        int conflicts = 0;
        try (var workers = Executors.newFixedThreadPool(2)) {
            Future<Integer> first = workers.submit(() -> patchOnce(tenantA, 9, ready, fire));
            Future<Integer> second = workers.submit(() -> patchOnce(tenantA, 10, ready, fire));
            assertTrue(ready.await(15, TimeUnit.SECONDS));
            fire.countDown();
            for (Future<Integer> result : List.of(first, second)) {
                if (result.get(40, TimeUnit.SECONDS) == 1) successes++;
                else conflicts++;
            }
        }
        assertEquals(1, successes);
        assertEquals(1, conflicts);
        assertEquals(1, asTenant(tenantA, service::list).size());
        assertEquals(List.of(new BusinessHourResponse(1, LocalTime.of(7, 0), LocalTime.of(15, 0))),
                asTenant(tenantB, service::list));
        assertEquals(1, context.callAsSystem(() -> hours.findAllByBusinessIdOrderByDayOfWeekAscOpenTimeAsc(tenantB)).size());
    }

    private int patchOnce(UUID tenant, int hour, CountDownLatch ready, CountDownLatch fire) throws Exception {
        ready.countDown();
        assertTrue(fire.await(15, TimeUnit.SECONDS));
        try {
            asTenant(tenant, () -> service.mergeSelected(new BusinessHoursSelectedRequest(
                    List.of(hour(1, hour, 18)), List.of())));
            return 1;
        } catch (ResponseStatusException conflict) {
            assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
            return 0;
        }
    }
}
