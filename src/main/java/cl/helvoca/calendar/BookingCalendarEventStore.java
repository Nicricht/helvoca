package cl.helvoca.calendar;

import cl.helvoca.booking.Booking;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class BookingCalendarEventStore {
    private static final RowMapper<BookingCalendarEvent> MAPPER = BookingCalendarEventStore::map;
    private final JdbcTemplate jdbc;

    public BookingCalendarEventStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public BookingCalendarEvent prepare(CalendarIntegration integration, Booking booking, String fingerprint) {
        if (integration == null || booking == null || fingerprint == null || fingerprint.length() != 64) {
            throw new IllegalArgumentException("integration, booking and SHA-256 fingerprint are required");
        }
        if (!integration.businessId().equals(booking.getBusinessId())) {
            throw new IllegalArgumentException("Calendar integration does not belong to booking tenant");
        }

        advisoryLock(booking.getBusinessId(), booking.getId());
        BookingCalendarEvent existing = findByBooking(booking.getBusinessId(), booking.getId()).orElse(null);
        if (existing == null) {
            UUID id = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO booking_calendar_event (
                        id, business_id, booking_id, integration_id, provider_code,
                        desired_version, synced_version, desired_fingerprint, status,
                        created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, 1, 0, ?, 'PENDING', NOW(), NOW())
                    """, id, booking.getBusinessId(), booking.getId(), integration.id(),
                    integration.providerCode(), fingerprint);
            return findById(booking.getBusinessId(), id).orElseThrow();
        }

        boolean sameBinding = existing.integrationId().equals(integration.id())
                && existing.providerCode().equalsIgnoreCase(integration.providerCode());
        if (!sameBinding && existing.externalEventId() != null && existing.status() != BookingCalendarEvent.Status.DELETED) {
            throw new IllegalStateException("Calendar provider changed while an external event is still active");
        }
        if (sameBinding && existing.desiredFingerprint().equals(fingerprint)) return existing;

        jdbc.update("""
                UPDATE booking_calendar_event
                   SET integration_id = ?,
                       provider_code = ?,
                       desired_version = desired_version + 1,
                       desired_fingerprint = ?,
                       status = 'PENDING',
                       last_error_code = NULL,
                       last_error_message = NULL,
                       external_event_id = CASE WHEN provider_code = ? THEN external_event_id ELSE NULL END,
                       meeting_url = CASE WHEN provider_code = ? THEN meeting_url ELSE NULL END,
                       updated_at = NOW()
                 WHERE id = ? AND business_id = ?
                """, integration.id(), integration.providerCode(), fingerprint,
                integration.providerCode(), integration.providerCode(), existing.id(), booking.getBusinessId());
        return findById(booking.getBusinessId(), existing.id()).orElseThrow();
    }

    @Transactional(readOnly = true)
    public Optional<BookingCalendarEvent> findById(UUID businessId, UUID id) {
        return jdbc.query("SELECT * FROM booking_calendar_event WHERE id = ? AND business_id = ?", MAPPER, id, businessId)
                .stream().findFirst();
    }

    @Transactional(readOnly = true)
    public Optional<BookingCalendarEvent> findByBooking(UUID businessId, UUID bookingId) {
        return jdbc.query("SELECT * FROM booking_calendar_event WHERE business_id = ? AND booking_id = ?", MAPPER, businessId, bookingId)
                .stream().findFirst();
    }

    @Transactional(readOnly = true)
    public List<BookingCalendarEvent> recent(UUID businessId) {
        return jdbc.query("""
                SELECT * FROM booking_calendar_event
                 WHERE business_id = ?
                 ORDER BY updated_at DESC
                 LIMIT 100
                """, MAPPER, businessId);
    }

    /**
     * External event identity is stable across booking revisions. Record it even
     * if the producing job became stale while the provider call was in flight.
     */
    @Transactional
    public void recordExternalEventId(UUID businessId,
                                      UUID id,
                                      String providerCode,
                                      String externalEventId) {
        if (externalEventId == null || externalEventId.isBlank()) {
            throw new IllegalArgumentException("externalEventId is required");
        }
        int updated = jdbc.update("""
                UPDATE booking_calendar_event
                   SET external_event_id = ?,
                       updated_at = NOW()
                 WHERE id = ?
                   AND business_id = ?
                   AND upper(provider_code) = upper(?)
                   AND (external_event_id IS NULL OR external_event_id = ?)
                """, externalEventId.trim(), id, businessId, providerCode, externalEventId.trim());
        if (updated != 1) {
            throw new IllegalStateException("External calendar event identity conflicts with current projection");
        }
    }

    @Transactional
    public boolean markSynced(UUID businessId,
                              UUID id,
                              int desiredVersion,
                              String externalEventId,
                              String meetingUrl) {
        return jdbc.update("""
                UPDATE booking_calendar_event
                   SET external_event_id = ?,
                       meeting_url = ?,
                       synced_version = ?,
                       status = 'SYNCED',
                       last_error_code = NULL,
                       last_error_message = NULL,
                       synced_at = NOW(),
                       updated_at = NOW()
                 WHERE id = ? AND business_id = ? AND desired_version = ?
                """, externalEventId, meetingUrl, desiredVersion, id, businessId, desiredVersion) == 1;
    }

    @Transactional
    public boolean markDeleted(UUID businessId, UUID id, int desiredVersion) {
        return jdbc.update("""
                UPDATE booking_calendar_event
                   SET meeting_url = NULL,
                       synced_version = ?,
                       status = 'DELETED',
                       last_error_code = NULL,
                       last_error_message = NULL,
                       synced_at = NOW(),
                       updated_at = NOW()
                 WHERE id = ? AND business_id = ? AND desired_version = ?
                """, desiredVersion, id, businessId, desiredVersion) == 1;
    }

    @Transactional
    public void markFailed(UUID businessId, UUID id, int desiredVersion, String code, String message) {
        jdbc.update("""
                UPDATE booking_calendar_event
                   SET status = 'FAILED',
                       last_error_code = ?,
                       last_error_message = ?,
                       updated_at = NOW()
                 WHERE id = ? AND business_id = ? AND desired_version = ?
                """, safe(code, 100), safe(message, 500), id, businessId, desiredVersion);
    }

    @Transactional(readOnly = true)
    public long activeExternalEventsForOtherProvider(UUID businessId, String providerCode) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM booking_calendar_event
                 WHERE business_id = ?
                   AND external_event_id IS NOT NULL
                   AND status <> 'DELETED'
                   AND upper(provider_code) <> upper(?)
                """, Long.class, businessId, providerCode);
        return count == null ? 0L : count;
    }

    private void advisoryLock(UUID businessId, UUID bookingId) {
        jdbc.execute("SELECT pg_advisory_xact_lock(" + businessId.hashCode() + "," + bookingId.hashCode() + ")");
    }

    private static BookingCalendarEvent map(ResultSet rs, int rowNum) throws SQLException {
        return new BookingCalendarEvent(
                rs.getObject("id", UUID.class),
                rs.getObject("business_id", UUID.class),
                rs.getObject("booking_id", UUID.class),
                rs.getObject("integration_id", UUID.class),
                rs.getString("provider_code"),
                rs.getString("external_event_id"),
                rs.getString("meeting_url"),
                rs.getInt("desired_version"),
                rs.getInt("synced_version"),
                rs.getString("desired_fingerprint"),
                BookingCalendarEvent.Status.valueOf(rs.getString("status")),
                rs.getString("last_error_code"),
                rs.getString("last_error_message"),
                instant(rs, "synced_at"),
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
