package cl.helvoca.call;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/calls")
public class CallController {
    private final CallQueryService service;

    public CallController(CallQueryService service) { this.service = service; }

    @GetMapping
    public Page<CallResponse> list(@PageableDefault(size = 25, sort = "startedAt") Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    public CallDetailResponse get(@PathVariable UUID id) { return service.get(id); }
}
