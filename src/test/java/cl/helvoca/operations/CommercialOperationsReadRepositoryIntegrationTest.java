package cl.helvoca.operations;

import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.security.TenantDatabaseContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
@SpringBootTest
class CommercialOperationsReadRepositoryIntegrationTest {

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

    @Autowired BusinessRepository businesses;
    @Autowired BusinessOperationRepository operations;
    @Autowired BusinessOrderRepository orders;
    @Autowired TenantDatabaseContext databaseContext;

    TenantDatabaseContext.Scope systemScope;

    @BeforeEach
    void enterSystemScope() {
        systemScope = databaseContext.useSystem();
    }

    @AfterEach
    void leaveSystemScope() {
        if (systemScope != null) systemScope.close();
    }

    @Test
    void recentOrdersAreBoundedInPostgresAndRemainTenantScoped() {
        Business tenantA = business("Perf Tenant A");
        Business tenantB = business("Perf Tenant B");

        createOrders(tenantA, 105);
        createOrders(tenantB, 2);

        List<BusinessOrder> tenantARecent =
                orders.findTop100ByBusinessIdOrderByCreatedAtDesc(tenantA.getId());
        List<BusinessOrder> tenantBRecent =
                orders.findTop100ByBusinessIdOrderByCreatedAtDesc(tenantB.getId());

        assertEquals(100, tenantARecent.size());
        assertEquals(2, tenantBRecent.size());
        assertEquals(tenantA.getId(), tenantARecent.getFirst().getBusinessId());
        assertEquals(tenantB.getId(), tenantBRecent.getFirst().getBusinessId());
    }

    private Business business(String name) {
        Business business = new Business();
        business.setName(name);
        return businesses.saveAndFlush(business);
    }

    private void createOrders(Business business, int count) {
        List<BusinessOperation> operationBatch = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            BusinessOperation operation = new BusinessOperation();
            operation.setBusinessId(business.getId());
            operation.setType(BusinessOperation.Type.ORDER);
            operation.setStatus(BusinessOperation.Status.CONFIRMED);
            operation.setSource(BusinessOrder.Source.API);
            operation.setFulfillmentType(BusinessOrder.FulfillmentType.PICKUP);
            operation.setSubtotal(BigDecimal.valueOf(1000));
            operation.setDeliveryFee(BigDecimal.ZERO);
            operation.setTotal(BigDecimal.valueOf(1000));
            operationBatch.add(operation);
        }
        operationBatch = operations.saveAllAndFlush(operationBatch);

        List<BusinessOrder> orderBatch = new ArrayList<>(count);
        for (BusinessOperation operation : operationBatch) {
            BusinessOrder order = new BusinessOrder();
            order.setBusinessId(business.getId());
            order.setOperationId(operation.getId());
            order.setFulfillmentType(BusinessOrder.FulfillmentType.PICKUP);
            order.setStatus(BusinessOrder.Status.CONFIRMED);
            order.setSubtotal(BigDecimal.valueOf(1000));
            order.setDeliveryFee(BigDecimal.ZERO);
            order.setTotal(BigDecimal.valueOf(1000));
            order.setCurrency("CLP");
            order.setSource(BusinessOrder.Source.API);
            orderBatch.add(order);
        }
        orders.saveAllAndFlush(orderBatch);
    }
}
