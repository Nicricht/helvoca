package cl.helvoca.onboarding;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BusinessImportControllerTest {

    @Test
    void controllerRequiresBusinessAdminRole() {
        PreAuthorize annotation = BusinessImportController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(annotation);
        assertEquals("hasRole('BUSINESS_ADMIN')", annotation.value());
    }

    @Test
    void previewAndApplyDelegateToSeparateSafePhases() {
        BusinessImportPreviewService previews = mock(BusinessImportPreviewService.class);
        BusinessImportApplyService applies = mock(BusinessImportApplyService.class);
        BusinessImportController controller = new BusinessImportController(previews, applies);

        MockMultipartFile file = new MockMultipartFile(
                "files", "productos.csv", "text/csv",
                "Producto,Precio\nPapas,3490".getBytes(StandardCharsets.UTF_8));
        var expectedPreview = new BusinessImportPreviewService.Preview(
                "Don Pepe", List.of(), List.of(), List.of(), false);
        when(previews.preview("Don Pepe", List.of(file))).thenReturn(expectedPreview);

        var request = new BusinessImportApplyService.ApplyRequest(List.of(
                new BusinessImportApplyService.ProductInput(
                        "Papas", null, null, "CLP", null, null, null, "productos.csv")));
        var expectedApply = new BusinessImportApplyService.ApplyResult(1, 0, 0, List.of());
        when(applies.apply(request)).thenReturn(expectedApply);

        assertSame(expectedPreview, controller.preview("Don Pepe", List.of(file)));
        assertSame(expectedApply, controller.apply(request));
    }
}
