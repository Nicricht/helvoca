package cl.helvoca.schedule;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** A conditional patch: only selected days can be changed. */
public record BusinessHoursSelectedRequest(
        @NotEmpty List<@Valid BusinessHoursRequest.Interval> hours,
        @NotNull List<@Valid BusinessHoursRequest.Interval> expectedSelectedDayHours
) {}
