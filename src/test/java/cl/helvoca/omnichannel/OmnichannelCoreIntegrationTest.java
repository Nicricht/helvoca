package cl.helvoca.omnichannel;

import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.call.CallDirection;
import cl.helvoca.call.CallSession;
import cl.helvoca.call.CallSessionRepository;
import cl.helvoca.call.CallStatus;
import cl.helvoca.customer.Customer;
import cl.helvoca.customer.CustomerRepository;
import cl.helvoca.messaging.MessagingConversation;
import cl.helvoca.messaging.MessagingConversationRepository;
import cl.helvoca.operations.BusinessOperation;
import cl.helvoca.operations.BusinessOperationRepository;
import cl.helvoca.operations.BusinessOrder;
import cl.helvoca.operations.ConversationOperationState;
import cl.helvoca.operations.ConversationOperationStateRepository;
import cl.helvoca.operations.ConversationStateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest
@Transactional
class OmnichannelCoreIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("app.seed.enabled", () -> "false");
    }

    @Autowired BusinessRepository businesses;
    @Autowired CustomerRepository customers;
    @Autowired CustomerIdentityService identities;
    @Autowired CallSessionRepository calls;
    @Autowired MessagingConversationRepository conversations;
    @Autowired BusinessOperationRepository operations;
    @Autowired ConversationStateService conversationState;
    @Autowired ConversationOperationStateRepository conversationStates;
    @Autowired OmnichannelSessionService omnichannel;
    @Autowired OmnichannelSessionRepository omnichannelSessions;
    @Autowired OmnichannelChannelSessionRepository channelSessions;

    @Test
    void phoneIdentityRequiresExplicitVerificationAndAmbiguityFailsClosedPerTenant() {
        Business tenantA = business("Tenant A");
        Business tenantB = business("Tenant B");
        Customer a1 = customer(tenantA, "A1", "+56 9 1111 1111");
        Customer a2 = customer(tenantA, "A2", "+56911111111");
        Customer b1 = customer(tenantB, "B1", "+56911111111");

        assertEquals("+56911111111", CustomerIdentityService.normalizePhone("whatsapp:+56 9 1111 1111"));
        assertEquals("+56911111111", CustomerIdentityService.normalizePhone("0056 9 1111 1111"));
        assertNull(CustomerIdentityService.normalizePhone("9 1111 1111"));

        identities.recordDeclaredPhone(tenantA.getId(), a1.getId(), a1.getPhone(), "TEST_DECLARED");
        assertTrue(identities.resolveVerifiedPhone(tenantA.getId(), "+56911111111").isEmpty());

        identities.recordProviderAssertedPhone(tenantA.getId(), a1.getId(), a1.getPhone(), "WHATSAPP_CHANNEL");
        assertTrue(identities.resolveVerifiedPhone(tenantA.getId(), "+56911111111").isEmpty());

        identities.verifyPhone(tenantA.getId(), a1.getId(), a1.getPhone(),
                CustomerIdentity.VerificationStatus.MANUAL_VERIFIED, "TEST_ADMIN");
        assertEquals(a1.getId(), identities.resolveVerifiedPhone(tenantA.getId(), "+56 9 1111 1111").orElseThrow());
        assertEquals(a1.getId(), customers.findFirstByBusinessIdAndPhone(
                tenantA.getId(), "+56 9 1111 1111").orElseThrow().getId());

        identities.verifyPhone(tenantA.getId(), a2.getId(), a2.getPhone(),
                CustomerIdentity.VerificationStatus.CUSTOMER_VERIFIED, "TEST_CUSTOMER");
        assertTrue(identities.resolveVerifiedPhone(tenantA.getId(), "+56911111111").isEmpty());
        assertTrue(customers.findFirstByBusinessIdAndPhone(tenantA.getId(), "+56911111111").isEmpty());

        identities.verifyPhone(tenantB.getId(), b1.getId(), b1.getPhone(),
                CustomerIdentity.VerificationStatus.MANUAL_VERIFIED, "TEST_ADMIN");
        assertEquals(b1.getId(), identities.resolveVerifiedPhone(tenantB.getId(), "+56911111111").orElseThrow());
        assertEquals(a2.getId(), customers.findFirstRawByBusinessIdAndPhone(
                tenantA.getId(), "+56911111111").orElseThrow().getId());
    }

    @Test
    void explicitSameCustomerSharesOperationStateAcrossVoiceAndWhatsApp() {
        Business business = business("Omnichannel tenant");
        Customer customer = customer(business, "Customer", "+56922222222");
        CallSession voice = call(business, customer, "+56922222222");
        MessagingConversation whatsapp = whatsapp(business, customer, "+56922222222");
        BusinessOperation operation = operation(business, customer, voice.getId());

        ConversationOperationState fromVoice = conversationState.apply(
                business.getId(), voice.getId(), BusinessOrder.Source.VOICE,
                operation.getId(), Map.of("phase", "QUOTED", "amount", 24990));

        ConversationOperationState fromWhatsApp = conversationState.find(
                business.getId(), whatsapp.getId(), BusinessOrder.Source.WHATSAPP);

        assertNotNull(fromVoice.getOmnichannelSessionId());
        assertNotNull(fromWhatsApp);
        assertEquals(fromVoice.getId(), fromWhatsApp.getId());
        assertEquals(fromVoice.getOmnichannelSessionId(), fromWhatsApp.getOmnichannelSessionId());
        assertEquals(operation.getId(), fromWhatsApp.getActiveOperationId());
        assertEquals("QUOTED", fromWhatsApp.getState().get("phase"));
        assertEquals(1L, conversationStates.count());
        assertEquals(1L, omnichannelSessions.count());
        assertEquals(2L, channelSessions.count());

        ConversationOperationState updated = conversationState.apply(
                business.getId(), whatsapp.getId(), BusinessOrder.Source.WHATSAPP,
                null, Map.of("phase", "CONFIRMED", "confirmationChannel", "WHATSAPP"));
        assertEquals(fromVoice.getId(), updated.getId());
        assertEquals(operation.getId(), updated.getActiveOperationId());
        assertEquals("CONFIRMED", updated.getState().get("phase"));

        ConversationOperationState voiceAgain = conversationState.find(
                business.getId(), voice.getId(), BusinessOrder.Source.VOICE);
        assertEquals("CONFIRMED", voiceAgain.getState().get("phase"));
        assertEquals("WHATSAPP", voiceAgain.getState().get("confirmationChannel"));
    }

    @Test
    void anonymousSourcesDoNotMergeAndTenantLookupCannotAdoptForeignSource() {
        Business tenantA = business("Anonymous A");
        Business tenantB = business("Anonymous B");
        CallSession voiceA = call(tenantA, null, "+56933333333");
        MessagingConversation whatsappA = whatsapp(tenantA, null, "+56933333333");

        ConversationOperationState voiceState = conversationState.apply(
                tenantA.getId(), voiceA.getId(), BusinessOrder.Source.VOICE,
                null, Map.of("phase", "VOICE_ONLY"));

        assertNull(voiceState.getOmnichannelSessionId());
        assertNull(conversationState.find(
                tenantA.getId(), whatsappA.getId(), BusinessOrder.Source.WHATSAPP));
        assertNull(omnichannel.resolve(
                tenantB.getId(), voiceA.getId(), BusinessOrder.Source.VOICE));
        assertEquals(0L, omnichannelSessions.count());
        assertEquals(0L, channelSessions.count());
    }

    @Test
    void existingChannelLinkCannotSilentlySwitchToAnotherCustomer() {
        Business business = business("Conflict tenant");
        Customer first = customer(business, "First", "+56944444444");
        Customer second = customer(business, "Second", "+56955555555");
        CallSession voice = call(business, first, "+56944444444");

        OmnichannelSession original = omnichannel.resolve(
                business.getId(), voice.getId(), BusinessOrder.Source.VOICE);
        assertNotNull(original);
        assertEquals(first.getId(), original.getCustomerId());

        voice.setCustomerId(second.getId());
        calls.saveAndFlush(voice);

        IllegalStateException error = assertThrows(IllegalStateException.class, () ->
                omnichannel.resolve(business.getId(), voice.getId(), BusinessOrder.Source.VOICE));
        assertTrue(error.getMessage().contains("different customer"));
        assertEquals(1L, omnichannelSessions.count());
    }

    @Test
    void samePhoneInDifferentTenantsProducesDifferentSessions() {
        Business tenantA = business("Tenant session A");
        Business tenantB = business("Tenant session B");
        Customer customerA = customer(tenantA, "A", "+56977777777");
        Customer customerB = customer(tenantB, "B", "+56977777777");
        CallSession callA = call(tenantA, customerA, "+56977777777");
        CallSession callB = call(tenantB, customerB, "+56977777777");

        OmnichannelSession sessionA = omnichannel.resolve(
                tenantA.getId(), callA.getId(), BusinessOrder.Source.VOICE);
        OmnichannelSession sessionB = omnichannel.resolve(
                tenantB.getId(), callB.getId(), BusinessOrder.Source.VOICE);

        assertNotNull(sessionA);
        assertNotNull(sessionB);
        assertNotEquals(sessionA.getId(), sessionB.getId());
        assertEquals(customerA.getId(), sessionA.getCustomerId());
        assertEquals(customerB.getId(), sessionB.getCustomerId());
        assertFalse(sessionA.getBusinessId().equals(sessionB.getBusinessId()));
    }

    private Business business(String name) {
        Business business = new Business();
        business.setName(name);
        return businesses.saveAndFlush(business);
    }

    private Customer customer(Business business, String name, String phone) {
        Customer customer = new Customer();
        customer.setBusinessId(business.getId());
        customer.setName(name);
        customer.setPhone(phone);
        return customers.saveAndFlush(customer);
    }

    private CallSession call(Business business, Customer customer, String caller) {
        CallSession call = new CallSession();
        call.setBusinessId(business.getId());
        call.setCustomerId(customer == null ? null : customer.getId());
        call.setTelephonyProvider("test");
        call.setProviderCallId("test-call-" + UUID.randomUUID());
        call.setCallerNumber(caller);
        call.setDestinationNumber("+56220000000");
        call.setDirection(CallDirection.INBOUND);
        call.setStatus(CallStatus.IN_PROGRESS);
        call.setStartedAt(Instant.now());
        return calls.saveAndFlush(call);
    }

    private MessagingConversation whatsapp(Business business, Customer customer, String sender) {
        MessagingConversation conversation = new MessagingConversation();
        conversation.setBusinessId(business.getId());
        conversation.setCustomerId(customer == null ? null : customer.getId());
        conversation.setChannel("whatsapp");
        conversation.setSender(sender);
        conversation.setRecipient("+56220000000");
        conversation.setOpenedAt(Instant.now());
        conversation.setLastMessageAt(Instant.now());
        return conversations.saveAndFlush(conversation);
    }

    private BusinessOperation operation(Business business, Customer customer, UUID sourceReferenceId) {
        BusinessOperation operation = new BusinessOperation();
        operation.setBusinessId(business.getId());
        operation.setCustomerId(customer.getId());
        operation.setSourceReferenceId(sourceReferenceId);
        operation.setType(BusinessOperation.Type.ORDER);
        operation.setStatus(BusinessOperation.Status.DRAFT);
        operation.setSource(BusinessOrder.Source.VOICE);
        return operations.saveAndFlush(operation);
    }
}
