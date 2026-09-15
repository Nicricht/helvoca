package cl.helvoca.omnichannel;

import cl.helvoca.call.CallSession;
import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.messaging.MessagingConversation;
import cl.helvoca.messaging.MessagingConversationRepository;
import cl.helvoca.operations.BusinessOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class OmnichannelSessionService {
    private final OmnichannelSessionRepository sessions;
    private final OmnichannelChannelSessionRepository channelSessions;
    private final CallSessionRepository calls;
    private final MessagingConversationRepository messagingConversations;
    private final CustomerRepository customers;
    private final CustomerIdentityService identities;
    private final JdbcTemplate jdbc;

    public OmnichannelSessionService(OmnichannelSessionRepository sessions,
                                     OmnichannelChannelSessionRepository channelSessions,
                                     CallSessionRepository calls,
                                     MessagingConversationRepository messagingConversations,
                                     CustomerRepository customers,
                                     CustomerIdentityService identities,
                                     JdbcTemplate jdbc) {
        this.sessions = sessions;
        this.channelSessions = channelSessions;
        this.calls = calls;
        this.messagingConversations = messagingConversations;
        this.customers = customers;
        this.identities = identities;
        this.jdbc = jdbc;
    }

    /**
     * Resolves a universal session only for a source that is already attached
     * to an explicit customer inside the same tenant. Unknown/anonymous sources
     * intentionally remain channel-local until identity has been established.
     *
     * The returned session is transaction-locked with a PostgreSQL advisory
     * lock. Callers that resolve the same omnichannel session from different
     * channels are therefore serialized before reading or mutating shared
     * conversation state.
     */
    @Transactional
    public OmnichannelSession resolve(UUID businessId,
                                      UUID sourceReferenceId,
                                      BusinessOrder.Source channel) {
        if (businessId == null || sourceReferenceId == null) return null;
        BusinessOrder.Source safeChannel = channel == null ? BusinessOrder.Source.API : channel;

        lock(sourceReferenceId);
        SourceContext source = sourceContext(businessId, sourceReferenceId, safeChannel);

        OmnichannelChannelSession existingLink = channelSessions
                .findByBusinessIdAndChannelAndSourceReferenceId(businessId, safeChannel, sourceReferenceId)
                .orElse(null);
        if (existingLink != null) {
            OmnichannelSession existingSession = sessions
                    .findByIdAndBusinessId(existingLink.getOmnichannelSessionId(), businessId)
                    .orElseThrow(() -> new IllegalStateException("Omnichannel channel link references an invalid tenant session"));
            if (source != null && source.customerId() != null
                    && !source.customerId().equals(existingSession.getCustomerId())) {
                throw new IllegalStateException("Channel source is already linked to a different customer");
            }
            lock(existingSession.getId());
            touch(existingSession, existingLink);
            return existingSession;
        }

        if (source == null || source.customerId() == null) return null;
        if (customers.findByIdAndBusinessId(source.customerId(), businessId).isEmpty()) {
            throw new IllegalStateException("Channel source customer does not belong to tenant");
        }

        lock(source.customerId());
        OmnichannelSession session = sessions
                .findFirstByBusinessIdAndCustomerIdAndStatusOrderByLastActivityAtDesc(
                        businessId, source.customerId(), OmnichannelSession.Status.ACTIVE)
                .orElseGet(() -> newSession(businessId, source.customerId()));
        lock(session.getId());

        OmnichannelChannelSession link = new OmnichannelChannelSession();
        link.setBusinessId(businessId);
        link.setOmnichannelSessionId(session.getId());
        link.setChannel(safeChannel);
        link.setSourceReferenceId(sourceReferenceId);
        link.setNormalizedAddress(CustomerIdentityService.normalizePhone(source.address()));
        link.setLastActivityAt(Instant.now());
        channelSessions.saveAndFlush(link);

        if (source.address() != null) {
            identities.recordProviderAssertedPhone(
                    businessId,
                    source.customerId(),
                    source.address(),
                    safeChannel == BusinessOrder.Source.WHATSAPP ? "WHATSAPP_CHANNEL" : "VOICE_CALLER_ID");
        }
        touch(session, link);
        return session;
    }

    @Transactional(readOnly = true)
    public OmnichannelSession findById(UUID businessId, UUID sessionId) {
        if (businessId == null || sessionId == null) return null;
        return sessions.findByIdAndBusinessId(sessionId, businessId).orElse(null);
    }

    private SourceContext sourceContext(UUID businessId,
                                        UUID sourceReferenceId,
                                        BusinessOrder.Source channel) {
        if (channel == BusinessOrder.Source.VOICE) {
            CallSession call = calls.findByIdAndBusinessId(sourceReferenceId, businessId).orElse(null);
            return call == null ? null : new SourceContext(call.getCustomerId(), call.getCallerNumber());
        }
        if (channel == BusinessOrder.Source.WHATSAPP) {
            MessagingConversation conversation = messagingConversations
                    .findByIdAndBusinessId(sourceReferenceId, businessId).orElse(null);
            return conversation == null ? null : new SourceContext(conversation.getCustomerId(), conversation.getSender());
        }
        return null;
    }

    private OmnichannelSession newSession(UUID businessId, UUID customerId) {
        OmnichannelSession session = new OmnichannelSession();
        session.setBusinessId(businessId);
        session.setCustomerId(customerId);
        session.setStatus(OmnichannelSession.Status.ACTIVE);
        session.setOpenedAt(Instant.now());
        session.setLastActivityAt(Instant.now());
        session.setRevision(1);
        return sessions.saveAndFlush(session);
    }

    private void touch(OmnichannelSession session, OmnichannelChannelSession link) {
        Instant now = Instant.now();
        session.setLastActivityAt(now);
        session.setRevision(session.getRevision() == null ? 1 : session.getRevision() + 1);
        sessions.save(session);
        link.setLastActivityAt(now);
        channelSessions.save(link);
    }

    private void lock(UUID value) {
        long msb = value.getMostSignificantBits();
        long lsb = value.getLeastSignificantBits();
        int key1 = (int) (msb ^ (msb >>> 32));
        int key2 = (int) (lsb ^ (lsb >>> 32));
        jdbc.execute("SELECT pg_advisory_xact_lock(" + key1 + "," + key2 + ")");
    }

    private record SourceContext(UUID customerId, String address) { }
}
