package cl.helvoca.security;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TenantDatabaseContextTest {

    @Test
    void defaultsToDeniedWithoutExplicitScope() {
        TenantDatabaseContext context = new TenantDatabaseContext();

        assertEquals(
                TenantDatabaseContext.Mode.DENIED,
                context.currentOrInternalSystem().mode());
    }

    @Test
    void restoresOuterScopeAfterNestedScopeCloses() {
        TenantDatabaseContext context = new TenantDatabaseContext();
        UUID businessId = UUID.randomUUID();

        try (TenantDatabaseContext.Scope tenant = context.useTenant(businessId)) {
            assertEquals(TenantDatabaseContext.Mode.TENANT, context.currentOrInternalSystem().mode());

            try (TenantDatabaseContext.Scope system = context.useSystem()) {
                assertEquals(TenantDatabaseContext.Mode.SYSTEM, context.currentOrInternalSystem().mode());
            }

            assertEquals(TenantDatabaseContext.Mode.TENANT, context.currentOrInternalSystem().mode());
        }

        assertEquals(TenantDatabaseContext.Mode.DENIED, context.currentOrInternalSystem().mode());
    }
}
