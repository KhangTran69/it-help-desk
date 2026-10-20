package com.example.helpdesk.web;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PhilippineTimeTest {
    private final PhilippineTime time = new PhilippineTime();

    @Test
    void convertsUtcToPhilippineTimeIncludingNextDayAndLabel() {
        LocalDateTime utc = LocalDateTime.of(2026, 10, 18, 13, 53);

        assertEquals("Oct 18, 2026", time.date(utc));
        assertEquals("Oct 18, 2026 9:53 PM PHT", time.dateTime(utc));
    }

    @Test
    void dateConversionUsesPhilippineCalendarDay() {
        LocalDateTime utc = LocalDateTime.of(2026, 10, 18, 18, 30);

        assertEquals("Oct 19, 2026", time.date(utc));
        assertEquals("Oct 19, 2026 2:30 AM PHT", time.dateTime(utc));
    }
}
