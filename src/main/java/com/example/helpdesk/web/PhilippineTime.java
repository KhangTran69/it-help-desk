package com.example.helpdesk.web;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component("phTime")
public class PhilippineTime {
    private static final ZoneId ZONE = ZoneId.of("Asia/Manila");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a 'PHT'");

    /** Database LocalDateTime values are kept as UTC wall-clock values; convert only for display. */
    public String date(LocalDateTime utcValue) {
        return toPhilippineTime(utcValue).format(DATE);
    }

    public String dateTime(LocalDateTime utcValue) {
        return toPhilippineTime(utcValue).format(DATE_TIME);
    }

    private LocalDateTime toPhilippineTime(LocalDateTime utcValue) {
        if (utcValue == null) return null;
        return utcValue.atZone(ZoneOffset.UTC).withZoneSameInstant(ZONE).toLocalDateTime();
    }
}
