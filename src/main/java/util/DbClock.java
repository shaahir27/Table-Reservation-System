package util;

import db.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Unified application clock synchronized with Oracle Database (fixes D5).
 * Ensures client UI, validation rules, and database queries always agree on
 * "Today" and current time regardless of client OS clock drift or timezone differences.
 * Supports injecting fixed clocks for deterministic unit testing.
 */
public class DbClock extends Clock {
    private static final ZoneId DEFAULT_ZONE = ZoneId.systemDefault();
    private static volatile Clock delegateClock = Clock.system(DEFAULT_ZONE);
    private static volatile long offsetMillis = 0;
    private static volatile boolean isFixedForTesting = false;

    private static final DbClock INSTANCE = new DbClock();

    public static DbClock getInstance() {
        return INSTANCE;
    }

    /**
     * Synchronizes the in-memory clock offset with Oracle's SYSTIMESTAMP.
     */
    public static synchronized void syncWithDatabase() {
        if (isFixedForTesting) return;
        try (Connection con = DatabaseConnection.getConnection()) {
            syncWithDatabase(con);
        } catch (Exception e) {
            AppLog.warning("Could not synchronize DbClock with Oracle DB; falling back to local clock: " + e.getMessage());
        }
    }

    public static synchronized void syncWithDatabase(Connection con) {
        if (isFixedForTesting) return;
        String sql = "SELECT SYSTIMESTAMP FROM dual";
        try (PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                Timestamp ts = rs.getTimestamp(1);
                if (ts != null) {
                    long dbMillis = ts.getTime();
                    long sysMillis = System.currentTimeMillis();
                    offsetMillis = dbMillis - sysMillis;
                    AppLog.info("DbClock synchronized with Oracle DB. Offset: " + offsetMillis + " ms");
                }
            }
        } catch (SQLException e) {
            AppLog.warning("Failed to query SYSTIMESTAMP for DbClock: " + e.getMessage());
        }
    }

    /**
     * Allows tests to lock time to a fixed Instant/Clock.
     */
    public static synchronized void setFixed(Clock fixedClock) {
        delegateClock = fixedClock;
        isFixedForTesting = true;
    }

    public static synchronized void reset() {
        isFixedForTesting = false;
        offsetMillis = 0;
        delegateClock = Clock.system(DEFAULT_ZONE);
    }

    public static Instant now() {
        return INSTANCE.instant();
    }

    public static LocalDate today() {
        return LocalDate.now(INSTANCE);
    }

    public static LocalTime localTime() {
        return LocalTime.now(INSTANCE);
    }

    public static LocalDateTime localDateTime() {
        return LocalDateTime.now(INSTANCE);
    }

    public static Clock clock() {
        return INSTANCE;
    }

    @Override
    public ZoneId getZone() {
        return delegateClock.getZone();
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return Clock.offset(Clock.system(zone), java.time.Duration.ofMillis(offsetMillis));
    }

    @Override
    public Instant instant() {
        if (isFixedForTesting) {
            return delegateClock.instant();
        }
        return Instant.ofEpochMilli(System.currentTimeMillis() + offsetMillis);
    }
}
