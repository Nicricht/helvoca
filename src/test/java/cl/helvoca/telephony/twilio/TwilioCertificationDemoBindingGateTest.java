package cl.helvoca.telephony.twilio;

import cl.helvoca.business.Business;
import cl.helvoca.business.BusinessMode;
import cl.helvoca.business.BusinessRepository;
import cl.helvoca.phone.PhoneNumber;
import cl.helvoca.phone.PhoneNumberRepository;
import cl.helvoca.security.TenantDatabaseContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TwilioCertificationDemoBindingGateTest {
    private static final UUID DEMO_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String FROM = "+16812706780";
    private static final String ACCOUNT = "AC" + "a".repeat(32);

    @Test
    void rejectsMissingPinBeforeDatabase() {
        var phones = mock(PhoneNumberRepository.class);
        var businesses = mock(BusinessRepository.class);
        var gate = new TwilioCertificationDemoBindingGate("", FROM, ACCOUNT,
                phones, businesses, mock(TwilioVerifiedExistingVoiceRouteGate.class), new TenantDatabaseContext());

        assertFalse(gate.allows(DEMO_ID));
        verifyNoInteractions(phones, businesses);
    }

    @Test
    void rejectsCustomerPhoneEvenIfItsNumberMatches() {
        var phones = mock(PhoneNumberRepository.class);
        var businesses = mock(BusinessRepository.class);
        var phone = certifiedPhone(DEMO_ID);
        var business = new Business();
        business.setMode(BusinessMode.CUSTOMER);
        when(phones.findByPhoneNumberAndActiveTrue(FROM)).thenReturn(Optional.of(phone));
        when(businesses.findById(DEMO_ID)).thenReturn(Optional.of(business));
        var gate = new TwilioCertificationDemoBindingGate(DEMO_ID.toString(), FROM, ACCOUNT,
                phones, businesses, mock(TwilioVerifiedExistingVoiceRouteGate.class), new TenantDatabaseContext());

        assertFalse(gate.allows(DEMO_ID));
    }

    @Test
    void requiresVerifiedExistingRouteEvidenceAndExactTenant() {
        var phones = mock(PhoneNumberRepository.class);
        var businesses = mock(BusinessRepository.class);
        var route = mock(TwilioVerifiedExistingVoiceRouteGate.class);
        var phone = certifiedPhone(DEMO_ID);
        var business = new Business();
        business.setMode(BusinessMode.DEMO);
        when(phones.findByPhoneNumberAndActiveTrue(FROM)).thenReturn(Optional.of(phone));
        when(businesses.findById(DEMO_ID)).thenReturn(Optional.of(business));
        var gate = new TwilioCertificationDemoBindingGate(DEMO_ID.toString(), FROM, ACCOUNT,
                phones, businesses, route, new TenantDatabaseContext());

        assertFalse(gate.allows(UUID.randomUUID()));
        assertFalse(gate.allows(DEMO_ID));
        when(route.allows(phone)).thenReturn(true);
        assertTrue(gate.allows(DEMO_ID));
    }

    private static PhoneNumber certifiedPhone(UUID businessId) {
        var phone = new PhoneNumber();
        phone.setBusinessId(businessId);
        phone.setPhoneNumber(FROM);
        phone.setProvider("TWILIO");
        phone.setExternalId("PN" + "b".repeat(32));
        phone.setTwilioAccountSid(ACCOUNT);
        phone.setTwilioBindingSource("VERIFIED_EXISTING");
        phone.setVoiceVerifiedAt(Instant.now());
        return phone;
    }
}
