package cl.helvoca.observability;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CorrelationIdFilterTest {
    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void acceptsBoundedSafeClientCorrelationIdAndClearsMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/jobs");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(CorrelationIdFilter.HEADER, "client-req_1234:abcd");
        AtomicReference<String> seenInsideChain = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) ->
                seenInsideChain.set(MDC.get(CorrelationIdFilter.MDC_KEY)));

        assertEquals("client-req_1234:abcd", seenInsideChain.get());
        assertEquals("client-req_1234:abcd", response.getHeader(CorrelationIdFilter.HEADER));
        assertNull(MDC.get(CorrelationIdFilter.MDC_KEY));
    }

    @Test
    void replacesUnsafeClientValueInsteadOfReflectingIt() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/jobs");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(CorrelationIdFilter.HEADER, "bad value with spaces and control-ish content");
        AtomicReference<String> seenInsideChain = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) ->
                seenInsideChain.set(MDC.get(CorrelationIdFilter.MDC_KEY)));

        String generated = response.getHeader(CorrelationIdFilter.HEADER);
        assertNotNull(generated);
        assertNotEquals("bad value with spaces and control-ish content", generated);
        assertEquals(generated, seenInsideChain.get());
        assertTrue(generated.matches("[A-Za-z0-9._:-]{8,128}"));
        assertNull(MDC.get(CorrelationIdFilter.MDC_KEY));
    }
}
