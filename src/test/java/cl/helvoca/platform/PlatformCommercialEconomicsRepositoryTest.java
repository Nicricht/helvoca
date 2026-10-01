package cl.helvoca.platform;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"unchecked", "rawtypes"})
class PlatformCommercialEconomicsRepositoryTest {

    @Test
    void mapsCurrentBusinessEconomicsSource() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ResultSet rs = mock(ResultSet.class);
        UUID businessId = UUID.randomUUID();

        when(rs.getObject("business_id", UUID.class)).thenReturn(businessId);
        when(rs.getString("business_name")).thenReturn("Negocio");
        when(rs.getString("plan_code")).thenReturn("BASIC");
        when(rs.getString("plan_name")).thenReturn("Emprende");
        when(rs.getString("status")).thenReturn("ACTIVE");
        when(rs.getObject("monthly_price_clp")).thenReturn(24990);
        when(rs.getBoolean("custom_pricing")).thenReturn(false);
        when(rs.getObject("included_seconds")).thenReturn(new BigDecimal("6000"));
        when(rs.getObject("used_seconds")).thenReturn(new BigDecimal("4200"));
        when(rs.getObject("overage_unit_size")).thenReturn(new BigDecimal("60"));
        when(rs.getObject("overage_price_clp")).thenReturn(149);
        when(rs.getInt("active_voice_number_count")).thenReturn(1);
        when(rs.getObject("estimated_cost_usd")).thenReturn(new BigDecimal("1.25"));

        when(jdbc.query(anyString(), any(RowMapper.class))).thenAnswer(invocation -> {
            RowMapper mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(rs, 0));
        });

        var rows = new PlatformCommercialEconomicsRepository(jdbc).currentBusinesses();

        assertEquals(1, rows.size());
        var row = rows.getFirst();
        assertEquals(businessId, row.businessId());
        assertEquals("Negocio", row.businessName());
        assertEquals(24990, row.monthlyPriceClp());
        assertFalse(row.customPricing());
        assertEquals(1, row.activeVoiceNumberCount());
        assertEquals(new BigDecimal("1.25"), row.estimatedCostUsd());
    }

    @Test
    void mapsProviderModelCostBreakdown() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ResultSet rs = mock(ResultSet.class);

        when(rs.getString("ai_provider")).thenReturn("gemini");
        when(rs.getString("ai_model")).thenReturn("gemini-3.8-live");
        when(rs.getLong("call_count")).thenReturn(4L);
        when(rs.getObject("duration_seconds")).thenReturn(new BigDecimal("6300"));
        when(rs.getObject("telephony_cost_usd")).thenReturn(new BigDecimal("1.1"));
        when(rs.getObject("ai_cost_usd")).thenReturn(new BigDecimal("3.9"));
        when(rs.getObject("total_cost_usd")).thenReturn(new BigDecimal("5.0"));

        when(jdbc.query(anyString(), any(RowMapper.class))).thenAnswer(invocation -> {
            RowMapper mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(rs, 0));
        });

        var rows = new PlatformCommercialEconomicsRepository(jdbc).providerBreakdown();

        assertEquals(1, rows.size());
        var row = rows.getFirst();
        assertEquals("gemini", row.aiProvider());
        assertEquals("gemini-3.8-live", row.aiModel());
        assertEquals(4L, row.callCount());
        assertEquals(new BigDecimal("5.0"), row.estimatedTotalCostUsd());
    }
}
