package cl.helvoca.omnichannel;

import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.messaging.MessagingConversation;
import cl.helvoca.messaging.MessagingConversationRepository;
import cl.helvoca.operations.BusinessOrder;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OmnichannelSessionServiceVerifiedRebindTest {

    @Test
    void verifiedWhatsappCustomerRebindsStaleChannelLink() {
        UUID businessId = UUID.randomUUID();
        UUID sourceReferenceId = UUID.randomUUID();
        UUID oldCustomerId = UUID.randomUUID();
        UUID currentCustomerId = UUID.randomUUID();
        UUID oldSessionId = UUID.randomUUID();
        UUID currentSessionId = UUID.randomUUID();
        String sender = "+56911112222";

        OmnichannelSessionRepository sessions = mock(OmnichannelSessionRepository.class);
        OmnichannelChannelSessionRepository channelSessions = mock(OmnichannelChannelSessionRepository.class);
        CallSessionRepository calls = mock(CallSessionRepository.class);
        MessagingConversationRepository conversations = mock(MessagingConversationRepository.class);
        CustomerRepository customers = mock(CustomerRepository.class);
        CustomerIdentityService identities = mock(CustomerIdentityService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);

        MessagingConversation conversation = mock(MessagingConversation.class);
        OmnichannelChannelSession link = mock(OmnichannelChannelSession.class);
        OmnichannelSession oldSession = mock(OmnichannelSession.class);
        OmnichannelSession currentSession = mock(OmnichannelSession.class);

        when(conversations.findByIdAndBusinessId(sourceReferenceId, businessId))
                .thenReturn(Optional.of(conversation));
        when(conversation.getCustomerId()).thenReturn(currentCustomerId);
        when(conversation.getSender()).thenReturn(sender);

        when(channelSessions.findByBusinessIdAndChannelAndSourceReferenceId(
                businessId, BusinessOrder.Source.WHATSAPP, sourceReferenceId))
                .thenReturn(Optional.of(link));
        when(link.getOmnichannelSessionId()).thenReturn(oldSessionId);
        when(sessions.findByIdAndBusinessId(oldSessionId, businessId))
                .thenReturn(Optional.of(oldSession));
        when(oldSession.getCustomerId()).thenReturn(oldCustomerId);

        when(identities.resolveVerifiedPhone(businessId, sender))
                .thenReturn(Optional.of(currentCustomerId));
        when(sessions.findFirstByBusinessIdAndCustomerIdAndStatusOrderByLastActivityAtDesc(
                businessId, currentCustomerId, OmnichannelSession.Status.ACTIVE))
                .thenReturn(Optional.of(currentSession));
        when(currentSession.getId()).thenReturn(currentSessionId);
        when(currentSession.getRevision()).thenReturn(1);

        OmnichannelSessionService service = new OmnichannelSessionService(
                sessions,
                channelSessions,
                calls,
                conversations,
                customers,
                identities,
                jdbc);

        OmnichannelSession resolved = service.resolve(
                businessId,
                sourceReferenceId,
                BusinessOrder.Source.WHATSAPP);

        assertSame(currentSession, resolved);
        verify(link).setOmnichannelSessionId(currentSessionId);
        verify(channelSessions).save(link);
    }
}
