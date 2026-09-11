package cl.helvoca.ai.live;

import cl.helvoca.ai.realtime.OpenAiRealtimeProperties;
import cl.helvoca.ai.realtime.RealtimeToolService;
import cl.helvoca.telephony.CallLifecycleService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class OpenAiLiveSipServiceTest {

    @Test
    void twimlRoutesToProjectSipEndpointWithSignedMetadata() {
        OpenAiRealtimeProperties openAi = new OpenAiRealtimeProperties();
        openAi.setApiKey("sk-test");

        OpenAiLiveProperties live = new OpenAiLiveProperties();
        live.setEnabled(true);
        live.setProjectId("proj_test123");
        live.setWebhookSecret("whsec-route-test");

        OpenAiLiveRouteSigner signer = new OpenAiLiveRouteSigner(live);
        OpenAiLiveSipService service = new OpenAiLiveSipService(
                openAi,
                live,
                signer,
                mock(CallLifecycleService.class),
                mock(RealtimeToolService.class),
                mock(OpenAiLiveSidebandManager.class));

        String twiml = service.twiml("+14355652512", "+56911111111");

        assertTrue(service.isReady());
        assertTrue(twiml.contains("sip:proj_test123@sip.api.openai.com;transport=tls"));
        assertTrue(twiml.contains("x-recepvoz-business=%2B14355652512"));
        assertTrue(twiml.contains("x-recepvoz-caller=%2B56911111111"));
        assertTrue(twiml.contains("&amp;x-recepvoz-route="));
    }

    @Test
    void liveSipStaysDisabledUntilAllAccountSettingsExist() {
        OpenAiRealtimeProperties openAi = new OpenAiRealtimeProperties();
        openAi.setApiKey("sk-test");
        OpenAiLiveProperties live = new OpenAiLiveProperties();
        live.setEnabled(true);
        live.setProjectId("proj_test123");

        OpenAiLiveSipService service = new OpenAiLiveSipService(
                openAi,
                live,
                new OpenAiLiveRouteSigner(live),
                mock(CallLifecycleService.class),
                mock(RealtimeToolService.class),
                mock(OpenAiLiveSidebandManager.class));

        assertFalse(service.isReady());
    }
}
