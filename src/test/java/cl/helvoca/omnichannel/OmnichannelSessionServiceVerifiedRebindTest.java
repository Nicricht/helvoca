package cl.helvoca.omnichannel;

import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.customer.Customer;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.messaging.MessagingConversation;
import cl.helvoca.messaging.MessagingConversationRepository;
import cl.helvoca.operations.BusinessOrder;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
        Customer currentCustomer = mock(Customer.class);

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

        when(customers.findByIdAndBusinessId(currentCustomerId, businessId))
                .thenReturn(Optional.of(currentCustomer));
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

    @Test
    void verifiedWhatsappCustomerRebindsWhenMetaSenderOmitsPlusPrefix() {
        UUID businessId = UUID.randomUUID();
        UUID sourceReferenceId = UUID.randomUUID();
        UUID oldCustomerId = UUID.randomUUID();
        UUID currentCustomerId = UUID.randomUUID();
        UUID oldSessionId = UUID.randomUUID();
        UUID currentSessionId = UUID.randomUUID();
        String metaSender = "56911112222";
        String canonicalSender = "+56911112222";

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
        Customer currentCustomer = mock(Customer.class);

        when(conversations.findByIdAndBusinessId(sourceReferenceId, businessId))
                .thenReturn(Optional.of(conversation));
        when(conversation.getCustomerId()).thenReturn(currentCustomerId);
        when(conversation.getSender()).thenReturn(metaSender);
        when(channelSessions.findByBusinessIdAndChannelAndSourceReferenceId(
                businessId, BusinessOrder.Source.WHATSAPP, sourceReferenceId))
                .thenReturn(Optional.of(link));
        when(link.getOmnichannelSessionId()).thenReturn(oldSessionId);
        when(sessions.findByIdAndBusinessId(oldSessionId, businessId))
                .thenReturn(Optional.of(oldSession));
        when(oldSession.getCustomerId()).thenReturn(oldCustomerId);
        when(customers.findByIdAndBusinessId(currentCustomerId, businessId))
                .thenReturn(Optional.of(currentCustomer));
        when(identities.resolveVerifiedPhone(businessId, canonicalSender))
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
        verify(identities).resolveVerifiedPhone(businessId, canonicalSender);
        verify(link).setOmnichannelSessionId(currentSessionId);
        verify(link).setNormalizedAddress(canonicalSender);
    }

    @Test
    void unverifiedWhatsappCustomerMismatchStillFailsClosed() {
        UUID businessId = UUID.randomUUID();
        UUID sourceReferenceId = UUID.randomUUID();
        UUID oldCustomerId = UUID.randomUUID();
        UUID currentCustomerId = UUID.randomUUID();
        UUID oldSessionId = UUID.randomUUID();
        String sender = "+56933334444";

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
        Customer currentCustomer = mock(Customer.class);

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
        when(customers.findByIdAndBusinessId(currentCustomerId, businessId))
                .thenReturn(Optional.of(currentCustomer));
        when(identities.resolveVerifiedPhone(businessId, sender)).thenReturn(Optional.empty());

        OmnichannelSessionService service = new OmnichannelSessionService(
                sessions,
                channelSessions,
                calls,
                conversations,
                customers,
                identities,
                jdbc);

        assertThrows(IllegalStateException.class, () -> service.resolve(
                businessId,
                sourceReferenceId,
                BusinessOrder.Source.WHATSAPP));

        verify(link, never()).setOmnichannelSessionId(org.mockito.ArgumentMatchers.any());
    }
}
