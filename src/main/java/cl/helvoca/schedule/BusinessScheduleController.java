package cl.helvoca.schedule;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/business")
public class BusinessScheduleController {
    private final BusinessScheduleService service;

    public BusinessScheduleController(BusinessScheduleService service) { this.service = service; }

    @GetMapping("/hours")
    @PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
    public List<HourResponse> hours() {
        return service.listHours().stream().map(HourResponse::from).toList();
    }

    @PutMapping("/hours")
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public List<HourResponse> replaceHours(@Valid @RequestBody HoursRequest request) {
        List<BusinessScheduleService.HourInput> periods = request.periods().stream()
                .map(p -> new BusinessScheduleService.HourInput(p.dayOfWeek(), p.openTime(), p.closeTime()))
                .toList();
        return service.replaceHours(periods).stream().map(HourResponse::from).toList();
    }

    @GetMapping("/schedule-exceptions")
    @PreAuthorize("hasAnyRole('BUSINESS_ADMIN','OPERATOR')")
    public List<ExceptionResponse> exceptions() {
        return service.listExceptions().stream().map(ExceptionResponse::from).toList();
    }

    @PutMapping("/schedule-exceptions")
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public ExceptionResponse upsertException(@Valid @RequestBody ExceptionRequest request) {
        return ExceptionResponse.from(service.upsertException(new BusinessScheduleService.ExceptionInput(
                request.date(), request.closed(), request.openTime(), request.closeTime(), request.reason())));
    }

    @DeleteMapping("/schedule-exceptions/{id}")
    @PreAuthorize("hasRole('BUSINESS_ADMIN')")
    public ResponseEntity<Void> deleteException(@PathVariable UUID id) {
        service.deleteException(id);
        return ResponseEntity.noContent().build();
    }

    public record HoursRequest(@NotNull @Valid List<HourRequest> periods) {}

    public record HourRequest(
            @Min(1) @Max(7) int dayOfWeek,
            @NotNull LocalTime openTime,
            @NotNull LocalTime closeTime) {}

    public record HourResponse(UUID id, int dayOfWeek, LocalTime openTime, LocalTime closeTime) {
        static HourResponse from(BusinessHour hour) {
            return new HourResponse(hour.getId(), hour.getDayOfWeek(), hour.getOpenTime(), hour.getCloseTime());
        }
    }

    public record ExceptionRequest(
            @NotNull LocalDate date,
            boolean closed,
            LocalTime openTime,
            LocalTime closeTime,
            @Size(max = 200) String reason) {}

    public record ExceptionResponse(
            UUID id,
            LocalDate date,
            boolean closed,
            LocalTime openTime,
            LocalTime closeTime,
            String reason) {
        static ExceptionResponse from(BusinessScheduleException exception) {
            return new ExceptionResponse(exception.getId(), exception.getExceptionDate(), exception.isClosed(),
                    exception.getOpenTime(), exception.getCloseTime(), exception.getReason());
        }
    }
}
