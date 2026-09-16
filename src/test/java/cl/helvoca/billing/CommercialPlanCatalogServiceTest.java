package cl.helvoca.billing;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

class CommercialPlanCatalogServiceTest {

    @Test
    void resolvesActivePublicPlanFromDatabaseAndLoadsGenericEntitlements() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForList(contains("FROM commercial_plan"), any(MapSqlParameterSource.class)))
                .thenReturn(List.of(planRow("PRO", "NEGOCIO", "Negocio", 39_990, true, true, 2)));
        when(jdbc.queryForList(contains("FROM commercial_plan_entitlement"), any(MapSqlParameterSource.class)))
                .thenReturn(List.of(
                        entitlementRow("VOICE_SECONDS", "USAGE", "VOICE_SECONDS", "15000", "SECONDS", false, "60", 129),
                        entitlementRow("CONCURRENT_CALLS", "CAPACITY", null, "3", "COUNT", true, null, null)));

        CommercialPlanCatalogService service = new CommercialPlanCatalogService(jdbc);
        var plan = service.findActiveByPublicCode(" negocio ");

        assertEquals("PRO", plan.code());
        assertEquals("NEGOCIO", plan.publicCode());
        assertEquals("Negocio", plan.displayName());
        assertEquals(39_990, plan.monthlyPriceClp());
        assertTrue(plan.recommended());
        assertEquals(2, plan.entitlements().size());
        assertEquals("VOICE_SECONDS", plan.entitlements().getFirst().key());
        assertEquals(new BigDecimal("15000"), plan.entitlements().getFirst().limitValue());
        assertEquals(129, plan.entitlements().getFirst().overagePriceClp());
    }

    @Test
    void missingOrInactivePublicPlanFailsClosed() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForList(contains("FROM commercial_plan"), any(MapSqlParameterSource.class)))
                .thenReturn(List.of());

        CommercialPlanCatalogService service = new CommercialPlanCatalogService(jdbc);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.findActiveByPublicCode("retired"));
        assertTrue(error.getMessage().toLowerCase().contains("plan"));
        verify(jdbc, never()).queryForList(contains("FROM commercial_plan_entitlement"), any(MapSqlParameterSource.class));
    }

    @Test
    void activePlansRemainOrderedByDatabaseCatalog() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForList(contains("FROM commercial_plan"), any(MapSqlParameterSource.class)))
                .thenReturn(List.of(
                        planRow("BASIC", "EMPRENDE", "Emprende", 24_990, false, false, 1),
                        planRow("PRO", "NEGOCIO", "Negocio", 39_990, true, true, 2)));
        when(jdbc.queryForList(contains("FROM commercial_plan_entitlement"), any(MapSqlParameterSource.class)))
                .thenReturn(List.of());

        CommercialPlanCatalogService service = new CommercialPlanCatalogService(jdbc);
        var plans = service.activePlans();

        assertEquals(List.of("BASIC", "PRO"), plans.stream().map(CommercialPlanCatalogService.Plan::code).toList());
        assertEquals(List.of("EMPRENDE", "NEGOCIO"), plans.stream().map(CommercialPlanCatalogService.Plan::publicCode).toList());
    }

    private static Map<String, Object> planRow(String code, String publicCode, String name, int price,
                                                boolean recommended, boolean active, int sortOrder) {
        return Map.ofEntries(
                Map.entry("code", code),
                Map.entry("public_code", publicCode),
                Map.entry("display_name", name),
                Map.entry("monthly_price_clp", price),
                Map.entry("currency", "CLP"),
                Map.entry("custom_pricing", false),
                Map.entry("recommended", recommended),
                Map.entry("active", active),
                Map.entry("sort_order", sortOrder));
    }

    private static Map<String, Object> entitlementRow(String key, String kind, String meterKey,
                                                       String limit, String unit, boolean hardLimit,
                                                       String overageUnitSize, Integer overagePrice) {
        java.util.HashMap<String, Object> row = new java.util.HashMap<>();
        row.put("entitlement_key", key);
        row.put("kind", kind);
        row.put("meter_key", meterKey);
        row.put("limit_value", new BigDecimal(limit));
        row.put("unit", unit);
        row.put("hard_limit", hardLimit);
        row.put("overage_unit_size", overageUnitSize == null ? null : new BigDecimal(overageUnitSize));
        row.put("overage_price_clp", overagePrice);
        return row;
    }
}
