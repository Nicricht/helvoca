package cl.helvoca.platform;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

class PlatformCommercialEconomicsControllerTest {

    @Test
    void delegatesPortfolioToService() {
        PlatformCommercialEconomicsService service = mock(PlatformCommercialEconomicsService.class);
        var expected = new PlatformCommercialEconomicsService.PortfolioEconomics(
                0, 0, 0, 0, BigDecimal.ZERO, null, null, null,
                BigDecimal.ZERO, 0, List.of(), List.of());
        when(service.portfolio()).thenReturn(expected);

        var result = new PlatformCommercialEconomicsController(service).portfolio();

        assertSame(expected, result);
        verify(service).portfolio();
    }
}
