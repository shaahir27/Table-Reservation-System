package model;

import java.util.Collections;
import java.util.List;

/**
 * Aggregated statistics and upcoming bookings for the dashboard.
 */
public class DashboardStats {
    private int todayCount;
    private int freeTablesNextSlot;
    private String nextSlotLabel;
    private int upcomingCount;
    private int cancelledCount;
    private int recoveredCoversCount;
    private List<ReservationDetail> upcoming;

    public DashboardStats() {
        this.upcoming = Collections.emptyList();
    }

    public DashboardStats(int todayCount, int freeTablesNextSlot, String nextSlotLabel, 
                          int upcomingCount, int cancelledCount, List<ReservationDetail> upcoming) {
        this(todayCount, freeTablesNextSlot, nextSlotLabel, upcomingCount, cancelledCount, 0, upcoming);
    }

    public DashboardStats(int todayCount, int freeTablesNextSlot, String nextSlotLabel, 
                          int upcomingCount, int cancelledCount, int recoveredCoversCount, List<ReservationDetail> upcoming) {
        this.todayCount = todayCount;
        this.freeTablesNextSlot = freeTablesNextSlot;
        this.nextSlotLabel = nextSlotLabel;
        this.upcomingCount = upcomingCount;
        this.cancelledCount = cancelledCount;
        this.recoveredCoversCount = recoveredCoversCount;
        this.upcoming = upcoming != null ? upcoming : Collections.emptyList();
    }

    public int getTodayCount() {
        return todayCount;
    }

    public void setTodayCount(int todayCount) {
        this.todayCount = todayCount;
    }

    public int getFreeTablesNextSlot() {
        return freeTablesNextSlot;
    }

    public void setFreeTablesNextSlot(int freeTablesNextSlot) {
        this.freeTablesNextSlot = freeTablesNextSlot;
    }

    public String getNextSlotLabel() {
        return nextSlotLabel;
    }

    public void setNextSlotLabel(String nextSlotLabel) {
        this.nextSlotLabel = nextSlotLabel;
    }

    public int getUpcomingCount() {
        return upcomingCount;
    }

    public void setUpcomingCount(int upcomingCount) {
        this.upcomingCount = upcomingCount;
    }

    public int getCancelledCount() {
        return cancelledCount;
    }

    public void setCancelledCount(int cancelledCount) {
        this.cancelledCount = cancelledCount;
    }

    public int getRecoveredCoversCount() {
        return recoveredCoversCount;
    }

    public void setRecoveredCoversCount(int recoveredCoversCount) {
        this.recoveredCoversCount = recoveredCoversCount;
    }

    public List<ReservationDetail> getUpcoming() {
        return upcoming;
    }

    public void setUpcoming(List<ReservationDetail> upcoming) {
        this.upcoming = upcoming != null ? upcoming : Collections.emptyList();
    }
}
