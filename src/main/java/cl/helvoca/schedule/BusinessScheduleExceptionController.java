package cl.helvoca.schedule;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/business/schedule-exceptions")
public class BusinessScheduleExceptionController {
    private final BusinessScheduleExceptionAdminService service;

    public BusinessScheduleExceptionController(BusinessScheduleExceptionAdminService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
    public List<BusinessScheduleExceptionAdminService.View> list() {
        return service.list();
    }

    @PutMapping("/{date}")
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public BusinessScheduleExceptionAdminService.View upsert(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody BusinessScheduleExceptionAdminService.Update update) {
        return service.upsert(date, update);
    }

    @DeleteMapping("/{date}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public void delete(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        service.delete(date);
    }
}
