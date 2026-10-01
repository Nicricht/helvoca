package cl.helvoca.billing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UsageMeterControllerTest {

    @Test
    void customerSummaryRedactsInternalProviderCosts() {
        UsageMeterService service = mock(UsageMeterService.class);
        Instant from = Instant.parse("2026-09-01T00:00:00Z");
        Instant to = Instant.parse("2026-10-01T00:00:00Z");
        when(service.summarize(from, to)).thenReturn(List.of(
                new UsageMeterService.UsageSummary(
                        "VOICE_SECONDS", "SECONDS", new BigDecimal("600"),
                        new BigDecimal("12.34"), new BigDecimal("11.11"), 2)
        ));

        var result = new UsageMeterController(service).summary(from, to);

        assertEquals(1, result.size());
        assertEquals("VOICE_SECONDS", result.getFirst().meterKey());
        assertEquals(new BigDecimal("600"), result.getFirst().quantity());
        assertEquals(2, result.getFirst().eventCount());

        Set<String> fields = java.util.Arrays.stream(
                        UsageMeterController.CustomerUsageSummary.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .collect(Collectors.toSet());
        assertFalse(fields.contains("estimatedCostUsd"));
        assertFalse(fields.contains("actualCostUsd"));
        verify(service).summarize(from, to);
    }
}
