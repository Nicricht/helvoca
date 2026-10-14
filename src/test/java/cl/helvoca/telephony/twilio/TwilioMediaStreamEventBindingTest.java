package cl.helvoca.telephony.twilio;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TwilioMediaStreamEventBindingTest {
    @Test
    void startMustRejectConflictingOuterAndNestedStreamIdentifiers() {
        JSONObject outer = new JSONObject().put("streamSid", "MZ-tenant-one");
        JSONObject nested = new JSONObject().put("streamSid", "MZ-tenant-one");
        assertTrue(TwilioMediaStreamEventBinding.consistentStart(outer, nested));
        nested.put("streamSid", "MZ-tenant-two");
        assertFalse(TwilioMediaStreamEventBinding.consistentStart(outer, nested));
    }

    @Test
    void acceptsLegacySinglePlacementButNotContradictoryCarrierStart() {
        JSONObject outer = new JSONObject().put("streamSid", "MZ-one");
        JSONObject nested = new JSONObject().put("streamSid", "MZ-one");
        JSONObject missing = new JSONObject();
        JSONObject blank = new JSONObject().put("streamSid", " ");
        assertTrue(TwilioMediaStreamEventBinding.consistentStart(outer, missing));
        assertTrue(TwilioMediaStreamEventBinding.consistentStart(missing, nested));
        assertTrue(TwilioMediaStreamEventBinding.consistentStart(blank, nested));
        assertTrue(TwilioMediaStreamEventBinding.consistentStart(outer, blank));
        assertTrue(TwilioMediaStreamEventBinding.consistentStart(missing, missing));
    }

    @Test
    void eachPostStartEventMustNameExactlyTheAdmittedStream() {
        JSONObject event = new JSONObject().put("streamSid", "MZ-admitted");
        assertTrue(TwilioMediaStreamEventBinding.matchesStream("MZ-admitted", event));
        assertFalse(TwilioMediaStreamEventBinding.matchesStream("MZ-foreign", event));
        assertFalse(TwilioMediaStreamEventBinding.matchesStream(null, event));
        assertFalse(TwilioMediaStreamEventBinding.matchesStream(" ", event));
        assertFalse(TwilioMediaStreamEventBinding.matchesStream("MZ-admitted", new JSONObject()));
    }

    @Test
    void utilityConstructorIsNotAccessibleToNormalCallers() throws Exception {
        var constructor = TwilioMediaStreamEventBinding.class.getDeclaredConstructor();
        assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()));
        constructor.setAccessible(true);
        assertNotNull(constructor.newInstance());
    }

}
