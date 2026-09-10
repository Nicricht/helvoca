package cl.helvoca.schedule;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;
import java.util.List;

public record BusinessHoursRequest(@NotNull List<@Valid Interval> hours) {
    public record Interval(
            @Min(1) @Max(7) int dayOfWeek,
            @NotNull LocalTime openTime,
            @NotNull LocalTime closeTime
    ) {}
}
