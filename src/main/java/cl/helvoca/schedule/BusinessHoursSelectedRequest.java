package cl.helvoca.schedule;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** A conditional patch: only selected days can be changed. */
public record BusinessHoursSelectedRequest(
        @NotEmpty @Valid List<BusinessHoursRequest.Interval> hours,
        @NotNull @Valid List<BusinessHoursRequest.Interval> expectedSelectedDayHours
) {}
