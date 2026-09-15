package cl.helvoca.calendar;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class CalendarIntegrationStore {
    private static final RowMapper<CalendarIntegration> MAPPER = CalendarIntegrationStore::map;
    private final JdbcTemplate jdbc;

    public CalendarIntegrationStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Optional<CalendarIntegration> findByBusinessId(UUID businessId) {
        return jdbc.query("SELECT * FROM calendar_integration WHERE business_id = ?", MAPPER, businessId)
                .stream().findFirst();
    }

    @Transactional(readOnly = true)
    public Optional<CalendarIntegration> findConnected(UUID businessId) {
        return jdbc.query("SELECT * FROM calendar_integration WHERE business_id = ? AND status = 'CONNECTED'", MAPPER, businessId)
                .stream().findFirst();
    }

    /**
     * Called only after provider-specific authorization has succeeded. No secrets
     * are persisted here; OAuth/token storage belongs to the provider adapter.
     */
    @Transactional
    public CalendarIntegration markConnected(UUID businessId,
                                             String providerCode,
                                             String externalCalendarId,
                                             boolean meetingsEnabled) {
        if (businessId == null) throw new IllegalArgumentException("businessId is required");
        if (providerCode == null || providerCode.isBlank()) throw new IllegalArgumentException("providerCode is required");
        if (externalCalendarId == null || externalCalendarId.isBlank()) throw new IllegalArgumentException("externalCalendarId is required");
        jdbc.update("""
                INSERT INTO calendar_integration (
                    id, business_id, provider_code, status, external_calendar_id,
                    meetings_enabled, connected_at, disconnected_at,
                    last_error_code, last_error_message, created_at, updated_at
                ) VALUES (?, ?, ?, 'CONNECTED', ?, ?, NOW(), NULL, NULL, NULL, NOW(), NOW())
                ON CONFLICT (business_id) DO UPDATE SET
                    provider_code = EXCLUDED.provider_code,
                    status = 'CONNECTED',
                    external_calendar_id = EXCLUDED.external_calendar_id,
                    meetings_enabled = EXCLUDED.meetings_enabled,
                    connected_at = NOW(),
                    disconnected_at = NULL,
                    last_error_code = NULL,
                    last_error_message = NULL,
                    updated_at = NOW()
                """, UUID.randomUUID(), businessId, providerCode.trim(), externalCalendarId.trim(), meetingsEnabled);
        return findByBusinessId(businessId).orElseThrow();
    }

    @Transactional
    public boolean disconnect(UUID businessId) {
        return jdbc.update("""
                UPDATE calendar_integration
                   SET status = 'DISCONNECTED',
                       disconnected_at = NOW(),
                       updated_at = NOW()
                 WHERE business_id = ? AND status <> 'DISCONNECTED'
                """, businessId) == 1;
    }

    @Transactional
    public void markError(UUID businessId, String code, String message) {
        jdbc.update("""
                UPDATE calendar_integration
                   SET status = 'ERROR',
                       last_error_code = ?,
                       last_error_message = ?,
                       updated_at = NOW()
                 WHERE business_id = ?
                """, safe(code, 100), safe(message, 500), businessId);
    }

    private static CalendarIntegration map(ResultSet rs, int rowNum) throws SQLException {
        return new CalendarIntegration(
                rs.getObject("id", UUID.class),
                rs.getObject("business_id", UUID.class),
                rs.getString("provider_code"),
                CalendarIntegration.Status.valueOf(rs.getString("status")),
                rs.getString("external_calendar_id"),
                rs.getBoolean("meetings_enabled"),
                instant(rs, "connected_at"),
                instant(rs, "disconnected_at"),
                rs.getString("last_error_code"),
                rs.getString("last_error_message"),
                instant(rs, "created_at"),
                instant(rs, "updated_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static String safe(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
