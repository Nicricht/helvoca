package cl.helvoca.schedule;

import java.time.LocalTime;

public record BusinessHourResponse(int dayOfWeek, LocalTime openTime, LocalTime closeTime) {
    public static BusinessHourResponse from(BusinessHour hour) {
        return new BusinessHourResponse(hour.getDayOfWeek(), hour.getOpenTime(), hour.getCloseTime());
    }
}
