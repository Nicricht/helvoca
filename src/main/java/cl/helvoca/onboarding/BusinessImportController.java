package cl.helvoca.onboarding;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/onboarding/import")
@PreAuthorize("hasRole('BUSINESS_ADMIN')")
public class BusinessImportController {
    private final BusinessImportPreviewService previews;
    private final BusinessImportApplyService applies;

    public BusinessImportController(BusinessImportPreviewService previews,
                                    BusinessImportApplyService applies) {
        this.previews = previews;
        this.applies = applies;
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BusinessImportPreviewService.Preview preview(
            @RequestParam String businessName,
            @RequestPart("files") List<MultipartFile> files) {
        return previews.preview(businessName, files);
    }

    @PostMapping(value = "/apply", consumes = MediaType.APPLICATION_JSON_VALUE)
    public BusinessImportApplyService.ApplyResult apply(
            @RequestBody BusinessImportApplyService.ApplyRequest request) {
        return applies.apply(request);
    }
}
