package model.analytics;

import java.time.LocalDate;

/**
 * Data point representing cover and booking volume for a specific service date.
 */
public class DailyCoverTrend {
    private final LocalDate date;
    private final int covers;
    private final int bookings;

    public DailyCoverTrend(LocalDate date, int covers, int bookings) {
        this.date = date;
        this.covers = covers;
        this.bookings = bookings;
    }

    public LocalDate getDate() {
        return date;
    }

    public int getCovers() {
        return covers;
    }

    public int getBookings() {
        return bookings;
    }
}
