package util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class DbClockTest {

    @AfterEach
    void tearDown() {
        DbClock.reset();
    }

    @Test
    @DisplayName("DbClock returns accurate local date and time under default system clock")
    void testDefaultClock() {
        LocalDate today = DbClock.today();
        assertNotNull(today);
        assertEquals(LocalDate.now(), today);

        LocalTime time = DbClock.localTime();
        assertNotNull(time);

        LocalDateTime dateTime = DbClock.localDateTime();
        assertNotNull(dateTime);
        assertEquals(today, dateTime.toLocalDate());
    }

    @Test
    @DisplayName("DbClock fixed clock locks today(), localTime(), and localDateTime() deterministically")
    void testFixedClock() {
        // Fix clock to 2026-10-15 18:30:00 UTC
        Instant fixedInstant = Instant.parse("2026-10-15T18:30:00Z");
        ZoneId zone = ZoneId.of("UTC");
        Clock fixedClock = Clock.fixed(fixedInstant, zone);

        DbClock.setFixed(fixedClock);

        assertEquals(fixedInstant, DbClock.now());
        assertEquals(LocalDate.of(2026, 10, 15), DbClock.today());
        assertEquals(LocalTime.of(18, 30, 0), DbClock.localTime());
        assertEquals(LocalDateTime.of(2026, 10, 15, 18, 30, 0), DbClock.localDateTime());

        // Reset and verify it returns to system time
        DbClock.reset();
        assertNotEquals(LocalDate.of(2026, 10, 15), DbClock.today());
    }

    @Test
    @DisplayName("DbClock implements java.time.Clock contract")
    void testClockContract() {
        DbClock clock = DbClock.getInstance();
        assertNotNull(clock.getZone());
        assertNotNull(clock.instant());
        assertNotNull(clock.withZone(ZoneId.of("UTC")));
    }
}
