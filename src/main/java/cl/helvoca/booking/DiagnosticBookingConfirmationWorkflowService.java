package cl.helvoca.booking;

import cl.helvoca.business.BusinessRepository;
import cl.helvoca.operations.BusinessOperationRepository;
import cl.helvoca.operations.BusinessOrder;
import cl.helvoca.operations.ConversationStateService;
import cl.helvoca.operations.UnexpectedOperationFailureDiagnostic;
import cl.helvoca.operations.UniversalConfirmationService;
import cl.helvoca.schedule.BusinessScheduleService;
import cl.helvoca.servicecatalog.ServiceItemRepository;
import org.json.JSONObject;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Primary
public class DiagnosticBookingConfirmationWorkflowService extends BookingConfirmationWorkflowService {
    private final UnexpectedOperationFailureDiagnostic diagnostics;

    public DiagnosticBookingConfirmationWorkflowService(
            BookingRepository bookings,
            BusinessOperationRepository operations,
            ServiceItemRepository services,
            BusinessScheduleService schedule,
            UniversalConfirmationService confirmations,
            ConversationStateService conversationState,
            BusinessRepository businesses,
            JdbcTemplate jdbc,
            UnexpectedOperationFailureDiagnostic diagnostics) {
        super(bookings, operations, services, schedule, confirmations, conversationState, businesses, jdbc);
        this.diagnostics = diagnostics;
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public JSONObject execute(UUID businessId,
                              UUID customerId,
                              UUID sourceReferenceId,
                              String trustedPhone,
                              BusinessOrder.Source source,
                              BookingSource bookingSource,
                              JSONObject args) {
        try {
            return super.execute(businessId, customerId, sourceReferenceId, trustedPhone, source, bookingSource, args);
        } catch (RuntimeException e) {
            diagnostics.record("create_booking", e);
            throw e;
        }
    }
}
