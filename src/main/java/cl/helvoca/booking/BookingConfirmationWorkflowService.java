package cl.helvoca.booking;

import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.operations.BusinessOperation;
import cl.helvoca.operations.BusinessOperationRepository;
import cl.helvoca.operations.BusinessOrder;
import cl.helvoca.operations.ConversationStateService;
import cl.helvoca.operations.UniversalConfirmationService;
import cl.helvoca.schedule.BusinessScheduleService;
import cl.helvoca.servicecatalog.ServiceItem;
import cl.helvoca.servicecatalog.ServiceItemRepository;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Two-phase BOOKING workflow shared by voice and WhatsApp.
 *
 * Phase 1 validates the requested slot and persists a universal BOOKING
 * operation in AWAITING_CONFIRMATION. Phase 2 accepts only the durable token
 * for that exact operation revision, revalidates the slot under transaction
 * locks and then creates the typed booking projection.
 *
 * The confirmation is customer/tenant scoped rather than channel scoped, so an
 * explicitly identified customer may hear a proposal on VOICE and confirm the
 * exact same revision on WHATSAPP.
 */
@Service
public class BookingConfirmationWorkflowService {
    private static final Logger log = LoggerFactory.getLogger(BookingConfirmationWorkflowService.class);
    private final BookingRepository bookings;
    private final BusinessOperationRepository operations;
    private final ServiceItemRepository services;
    private final BusinessScheduleService schedule;
    private final UniversalConfirmationService confirmations;
    private final ConversationStateService conversationState;
    private final BusinessRepository businesses;
    private final JdbcTemplate jdbc;

    public BookingConfirmationWorkflowService(BookingRepository bookings,
                                              BusinessOperationRepository operations,
                                              ServiceItemRepository services,
                                              BusinessScheduleService schedule,
                                              UniversalConfirmationService confirmations,
                                              ConversationStateService conversationState,
                                              BusinessRepository businesses,
                                              JdbcTemplate jdbc) {
        this.bookings = bookings;
        this.operations = operations;
        this.services = services;
        this.schedule = schedule;
        this.confirmations = confirmations;
        this.conversationState = conversationState;
        this.businesses = businesses;
        this.jdbc = jdbc;
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public JSONObject execute(UUID businessId,
                              UUID customerId,
                              UUID sourceReferenceId,
                              String trustedPhone,
                              BusinessOrder.Source source,
                              BookingSource bookingSource,
                              JSONObject args) {
        if (businessId == null) return error("BUSINESS_CONTEXT_REQUIRED", "No puedo verificar el negocio actual.");
        if (customerId == null) return error("CUSTOMER_NOT_REGISTERED", "Necesito identificar al cliente antes de confirmar la reserva.");
        JSONObject safeArgs = args == null ? new JSONObject() : args;

        boolean hasOperation = !blank(safeArgs.optString("operationId", null));
        boolean hasToken = !blank(safeArgs.optString("confirmationToken", null));
        if (hasOperation || hasToken) {
            if (!hasOperation || !hasToken) {
                return error("INVALID_CONFIRMATION", "La confirmación requiere operationId y confirmationToken.");
            }
            return confirm(businessId, customerId, sourceReferenceId, trustedPhone,
                    source, bookingSource, safeArgs);
        }
        return propose(businessId, customerId, sourceReferenceId, trustedPhone,
                source, safeArgs);
    }

    private JSONObject propose(UUID businessId,
                               UUID customerId,
                               UUID sourceReferenceId,
                               String trustedPhone,
                               BusinessOrder.Source source,
                               JSONObject args) {
        UUID serviceId;
        Instant startAt;
        try {
            serviceId = UUID.fromString(required(args, "serviceId"));
            startAt = Instant.parse(required(args, "startAt"));
        } catch (Exception e) {
            return error("INVALID_BOOKING_ARGUMENTS", "El servicio o la fecha/hora de la reserva no son válidos.");
        }
        if (!startAt.isAfter(Instant.now())) {
            return error("BOOKING_TIME_NOT_FUTURE", "La fecha/hora de la reserva debe estar en el futuro.");
        }

        ServiceItem service = activeService(businessId, serviceId);
        if (service == null) return error("SERVICE_NOT_FOUND", "El servicio solicitado no está disponible.");
        Instant endAt = startAt.plus(service.getDurationMinutes(), ChronoUnit.MINUTES);
        JSONObject availabilityError = availabilityError(businessId, serviceId, startAt, endAt);
        if (availabilityError != null) return availabilityError;

        BusinessOrder.Source safeSource = source == null ? BusinessOrder.Source.API : source;
        UUID token = UUID.randomUUID();
        BusinessOperation operation = new BusinessOperation();
        operation.setBusinessId(businessId);
        operation.setCustomerId(customerId);
        operation.setSourceReferenceId(sourceReferenceId);
        operation.setType(BusinessOperation.Type.BOOKING);
        operation.setStatus(BusinessOperation.Status.AWAITING_CONFIRMATION);
        operation.setSource(safeSource);
        operation.setRevision(1);
        operation.setConfirmationToken(token);
        operation.setContactPhone(blank(trustedPhone) ? null : trustedPhone.trim());
        operation.setTotal(service.getPrice());
        operation.setCurrency("CLP");

        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("intent", "BOOKING");
        metadata.put("mutation", "CREATE");
        metadata.put("serviceId", serviceId.toString());
        metadata.put("serviceName", service.getName());
        metadata.put("startAt", startAt.toString());
        metadata.put("endAt", endAt.toString());
        String notes = optional(args, "notes");
        if (!blank(notes)) metadata.put("notes", notes);
        metadata.put("confirmationPending", true);
        operation.setMetadata(metadata);
        operation = operations.saveAndFlush(operation);

        recordConversation(operation, sourceReferenceId, safeSource, null);
        return success(proposalData(operation, service, startAt, endAt));
    }

    private JSONObject confirm(UUID businessId,
                               UUID customerId,
                               UUID sourceReferenceId,
                               String trustedPhone,
                               BusinessOrder.Source source,
                               BookingSource bookingSource,
                               JSONObject args) {
        UUID operationId;
        UUID token;
        try {
            operationId = UUID.fromString(required(args, "operationId"));
            token = UUID.fromString(required(args, "confirmationToken"));
        } catch (Exception e) {
            return error("INVALID_CONFIRMATION", "La confirmación requiere identificadores válidos.");
        }

        UniversalConfirmationService.Authorization authorization = confirmations.authorize(
                businessId, operationId, customerId, sourceReferenceId, trustedPhone, token);
        if (authorization == UniversalConfirmationService.Authorization.IDEMPOTENT_REPLAY) {
            Booking existing = bookings.findByOperationIdAndBusinessId(operationId, businessId).orElse(null);
            if (existing == null) {
                return error("BOOKING_PROJECTION_MISSING", "La reserva confirmada no tiene una proyección operativa válida.");
            }
            ServiceItem service = services.findByIdAndBusinessId(existing.getServiceId(), businessId).orElse(null);
            JSONObject data = bookingData(businessId, existing, service);
            data.put("operationId", operationId.toString());
            data.put("idempotentReplay", true);
            return success(data);
        }
        JSONObject authError = authorizationError(authorization);
        if (authError != null) return authError;

        // Serialize confirmations of the same operation before touching a slot.
        advisoryLock(businessId, operationId);
        authorization = confirmations.authorize(
                businessId, operationId, customerId, sourceReferenceId, trustedPhone, token);
        if (authorization == UniversalConfirmationService.Authorization.IDEMPOTENT_REPLAY) {
            Booking existing = bookings.findByOperationIdAndBusinessId(operationId, businessId).orElse(null);
            if (existing == null) return error("BOOKING_PROJECTION_MISSING", "La reserva confirmada no existe.");
            ServiceItem service = services.findByIdAndBusinessId(existing.getServiceId(), businessId).orElse(null);
            JSONObject data = bookingData(businessId, existing, service);
            data.put("operationId", operationId.toString());
            data.put("idempotentReplay", true);
            return success(data);
        }
        authError = authorizationError(authorization);
        if (authError != null) return authError;

        BusinessOperation operation = operations.findByIdAndBusinessId(operationId, businessId).orElse(null);
        if (operation == null || operation.getType() != BusinessOperation.Type.BOOKING) {
            return error("BOOKING_OPERATION_NOT_FOUND", "No encuentro esa propuesta de reserva.");
        }
        Map<String, Object> metadata = operation.getMetadata();
        UUID serviceId = metadataUuid(metadata, "serviceId");
        Instant startAt = metadataInstant(metadata, "startAt");
        if (serviceId == null || startAt == null) {
            expire(operation, "BOOKING_PROPOSAL_CORRUPT");
            return error("BOOKING_PROPOSAL_INVALID", "La propuesta ya no conserva condiciones válidas.");
        }

        advisoryLock(businessId, serviceId);
        ServiceItem service = activeService(businessId, serviceId);
        if (service == null) {
            expire(operation, "SERVICE_UNAVAILABLE");
            return error("SERVICE_NOT_FOUND", "El servicio ya no está disponible. Debo preparar una nueva propuesta.");
        }
        if (!startAt.isAfter(Instant.now())) {
            expire(operation, "BOOKING_TIME_EXPIRED");
            return error("CONFIRMATION_EXPIRED", "El horario de la propuesta ya pasó. Debo preparar una nueva propuesta.");
        }
        Instant endAt = startAt.plus(service.getDurationMinutes(), ChronoUnit.MINUTES);
        JSONObject availabilityError = availabilityError(businessId, serviceId, startAt, endAt);
        if (availabilityError != null) {
            expire(operation, "BOOKING_SLOT_UNAVAILABLE");
            return availabilityError;
        }

        Booking booking = new Booking();
        booking.setOperationId(operationId);
        booking.setBusinessId(businessId);
        booking.setCustomerId(customerId);
        booking.setServiceId(serviceId);
        booking.setStartAt(startAt);
        booking.setEndAt(endAt);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setSource(bookingSource == null ? bookingSourceFor(source) : bookingSource);
        booking.setNotes(metadataString(metadata, "notes"));
        booking = bookings.saveAndFlush(booking);

        BusinessOrder.Source safeSource = source == null ? operation.getSource() : source;
        LinkedHashMap<String, Object> resolved = new LinkedHashMap<>();
        if (metadata != null) resolved.putAll(metadata);
        resolved.put("bookingId", booking.getId().toString());
        resolved.put("serviceId", serviceId.toString());
        resolved.put("serviceName", service.getName());
        resolved.put("startAt", startAt.toString());
        resolved.put("endAt", endAt.toString());
        resolved.put("projectionStatus", booking.getStatus().name());
        resolved.put("confirmationPending", false);
        operation.setMetadata(resolved);
        operation.setSource(safeSource);
        operation.setSourceReferenceId(sourceReferenceId);
        operation.setStatus(BusinessOperation.Status.CONFIRMED);
        operation.setConfirmationToken(null);
        operations.saveAndFlush(operation);

        confirmations.recordResolution(businessId, operationId, token, safeSource, sourceReferenceId);
        recordConversation(operation, sourceReferenceId, safeSource, booking);
        registerBookingConfirmedEvidence(businessId, booking, operationId);

        JSONObject data = bookingData(businessId, booking, service);
        data.put("operationId", operationId.toString());
        data.put("operationRevision", operation.getRevision());
        data.put("idempotentReplay", false);
        return success(data);
    }

    private void registerBookingConfirmedEvidence(UUID businessId, Booking booking, UUID operationId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;

        UUID bookingId = booking.getId();
        Instant startAt = booking.getStartAt();
        BookingSource source = booking.getSource();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.info("BOOKING_CONFIRMED businessId={} bookingId={} operationId={} startAt={} source={}",
                        businessId, bookingId, operationId, startAt, source);
            }
        });
    }

    private JSONObject availabilityError(UUID businessId, UUID serviceId, Instant startAt, Instant endAt) {
        if (!schedule.isWithinBusinessHours(businessId, startAt, endAt)) {
            return error("BUSINESS_CLOSED", "Ese horario está fuera del horario de atención configurado.");
        }
        if (bookings.countOverlaps(businessId, serviceId, startAt, endAt, BookingStatus.CANCELLED, null) > 0) {
            return error("BOOKING_SLOT_UNAVAILABLE", "Ese horario ya no está disponible. Debo preparar una nueva propuesta.");
        }
        return null;
    }

    private void expire(BusinessOperation operation, String reason) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        if (operation.getMetadata() != null) metadata.putAll(operation.getMetadata());
        metadata.put("confirmationPending", false);
        metadata.put("confirmationInvalidatedReason", reason);
        operation.setMetadata(metadata);
        operation.setStatus(BusinessOperation.Status.EXPIRED);
        operation.setConfirmationToken(null);
        operations.saveAndFlush(operation);
    }

    private void recordConversation(BusinessOperation operation,
                                    UUID sourceReferenceId,
                                    BusinessOrder.Source source,
                                    Booking booking) {
        if (sourceReferenceId == null) return;
        LinkedHashMap<String, Object> patch = new LinkedHashMap<>();
        patch.put("intent", "BOOKING");
        patch.put("operationId", operation.getId().toString());
        patch.put("operationType", "BOOKING");
        patch.put("operationStatus", operation.getStatus().name());
        patch.put("operationRevision", operation.getRevision());
        boolean awaitingConfirmation = operation.getStatus() == BusinessOperation.Status.AWAITING_CONFIRMATION;
        patch.put("confirmationPending", awaitingConfirmation);
        patch.put("confirmationToken", operation.getConfirmationToken() == null ? null : operation.getConfirmationToken().toString());
        patch.put("bookingFlowStage", awaitingConfirmation
                ? "WAITING_CONFIRMATION"
                : operation.getStatus() == BusinessOperation.Status.CONFIRMED ? "CONFIRMED" : operation.getStatus().name());

        // A new proposal is a new booking operation. Remove terminal projection
        // fields from the previous booking so the conversation state cannot
        // accidentally look both CONFIRMED and AWAITING_CONFIRMATION.
        if (booking == null && awaitingConfirmation) {
            patch.put("bookingId", null);
            patch.put("bookingStatus", null);
        }
        if (operation.getMetadata() != null) {
            copy(operation.getMetadata(), patch, "serviceId");
            copy(operation.getMetadata(), patch, "serviceName");
            copy(operation.getMetadata(), patch, "startAt");
            copy(operation.getMetadata(), patch, "endAt");
        }
        if (booking != null) {
            patch.put("bookingId", booking.getId().toString());
            patch.put("bookingStatus", booking.getStatus().name());
        }
        conversationState.apply(
                operation.getBusinessId(),
                sourceReferenceId,
                source == null ? BusinessOrder.Source.API : source,
                operation.getId(),
                patch);
    }

    private JSONObject proposalData(BusinessOperation operation,
                                    ServiceItem service,
                                    Instant startAt,
                                    Instant endAt) {
        return new JSONObject()
                .put("operationId", operation.getId().toString())
                .put("operationRevision", operation.getRevision())
                .put("status", operation.getStatus().name())
                .put("confirmationToken", operation.getConfirmationToken().toString())
                .put("requiresConfirmation", true)
                .put("bookingCreated", false)
                .put("serviceId", service.getId().toString())
                .put("service", service.getName())
                .put("startAt", startAt.toString())
                .put("endAt", endAt.toString())
                .put("localStart", formatLocal(operation.getBusinessId(), startAt));
    }

    private JSONObject bookingData(UUID businessId, Booking booking, ServiceItem service) {
        String serviceName = service == null ? "Servicio" : service.getName();
        return new JSONObject()
                .put("bookingId", booking.getId().toString())
                .put("status", booking.getStatus().name())
                .put("serviceId", booking.getServiceId().toString())
                .put("service", serviceName)
                .put("startAt", booking.getStartAt().toString())
                .put("endAt", booking.getEndAt().toString())
                .put("localStart", formatLocal(businessId, booking.getStartAt()));
    }

    private JSONObject authorizationError(UniversalConfirmationService.Authorization authorization) {
        return switch (authorization) {
            case AUTHORIZED, IDEMPOTENT_REPLAY -> null;
            case EXPIRED -> error("CONFIRMATION_EXPIRED",
                    "La confirmación expiró. Debo preparar nuevamente la propuesta vigente.");
            case STALE -> error("STALE_CONFIRMATION",
                    "La confirmación no corresponde a la versión vigente. Debo presentar nuevamente la propuesta.");
            case NOT_AWAITING -> error("OPERATION_NOT_AWAITING_CONFIRMATION",
                    "La propuesta ya no está esperando confirmación.");
            case NOT_OWNED, NOT_FOUND -> error("BOOKING_OPERATION_NOT_FOUND",
                    "No encuentro esa propuesta entre las operaciones del cliente actual.");
        };
    }

    private ServiceItem activeService(UUID businessId, UUID serviceId) {
        return services.findByIdAndBusinessId(serviceId, businessId).filter(ServiceItem::isActive).orElse(null);
    }

    private void advisoryLock(UUID businessId, UUID resourceId) {
        int businessKey = businessId.hashCode();
        int resourceKey = resourceId.hashCode();
        jdbc.execute("SELECT pg_advisory_xact_lock(" + businessKey + "," + resourceKey + ")");
    }

    private String formatLocal(UUID businessId, Instant instant) {
        try {
            Business business = businesses.findById(businessId).orElse(null);
            ZoneId zone = ZoneId.of(business == null || blank(business.getTimezone()) ? "UTC" : business.getTimezone());
            return ZonedDateTime.ofInstant(instant, zone).toOffsetDateTime().toString();
        } catch (Exception ignored) {
            return instant.toString();
        }
    }

    private static BookingSource bookingSourceFor(BusinessOrder.Source source) {
        if (source == BusinessOrder.Source.WHATSAPP) return BookingSource.AI_WHATSAPP;
        if (source == BusinessOrder.Source.VOICE) return BookingSource.AI_CALL;
        return BookingSource.ADMIN;
    }

    private static UUID metadataUuid(Map<String, Object> metadata, String key) {
        String value = metadataString(metadata, key);
        try { return blank(value) ? null : UUID.fromString(value); }
        catch (Exception e) { return null; }
    }

    private static Instant metadataInstant(Map<String, Object> metadata, String key) {
        String value = metadataString(metadata, key);
        try { return blank(value) ? null : Instant.parse(value); }
        catch (Exception e) { return null; }
    }

    private static String metadataString(Map<String, Object> metadata, String key) {
        if (metadata == null || metadata.get(key) == null) return null;
        String value = String.valueOf(metadata.get(key));
        return blank(value) ? null : value;
    }

    private static void copy(Map<String, Object> from, Map<String, Object> to, String key) {
        if (from.get(key) != null) to.put(key, from.get(key));
    }

    private static String required(JSONObject args, String key) {
        String value = args.optString(key, null);
        if (blank(value)) throw new IllegalArgumentException("Falta el argumento " + key + ".");
        return value.trim();
    }

    private static String optional(JSONObject args, String key) {
        if (!args.has(key) || args.isNull(key)) return null;
        String value = args.optString(key, null);
        return blank(value) ? null : value.trim();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static JSONObject success(JSONObject data) {
        return new JSONObject().put("success", true).put("data", data).put("error", JSONObject.NULL);
    }

    private static JSONObject error(String code, String message) {
        return new JSONObject()
                .put("success", false)
                .put("data", JSONObject.NULL)
                .put("error", new JSONObject().put("code", code).put("message", message));
    }
}
