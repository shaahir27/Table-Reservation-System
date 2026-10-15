package model.analytics;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Comprehensive executive analytics snapshot for a selected date window.
 */
public class AnalyticsSummary {
    private final LocalDate fromDate;
    private final LocalDate toDate;

    // Executive Scorecard
    private int totalCovers;
    private int totalParties;
    private double avgPartySize;
    private double seatUtilizationPct;
    private int noShowCount;
    private double noShowPct;
    private int cancellationCount;
    private double cancellationPct;
    private int repeatCustomerCount;
    private double repeatCustomerPct;
    private int recoveredCovers;

    // Analytical Suites
    private List<DailyCoverTrend> dailyTrends = new ArrayList<>();
    private List<SlotLoadMetric> slotMetrics = new ArrayList<>();
    private List<TablePerformanceMetric> tableMetrics = new ArrayList<>();
    private List<TopGuestMetric> topGuests = new ArrayList<>();
    private List<TagCountMetric> tagDistribution = new ArrayList<>();
    private List<WaitlistFunnelStep> waitlistFunnel = new ArrayList<>();
    private List<String> keyTakeaways = new ArrayList<>();

    public AnalyticsSummary(LocalDate fromDate, LocalDate toDate) {
        this.fromDate = fromDate;
        this.toDate = toDate;
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public int getTotalCovers() {
        return totalCovers;
    }

    public void setTotalCovers(int totalCovers) {
        this.totalCovers = totalCovers;
    }

    public int getTotalParties() {
        return totalParties;
    }

    public void setTotalParties(int totalParties) {
        this.totalParties = totalParties;
    }

    public double getAvgPartySize() {
        return avgPartySize;
    }

    public void setAvgPartySize(double avgPartySize) {
        this.avgPartySize = avgPartySize;
    }

    public double getSeatUtilizationPct() {
        return seatUtilizationPct;
    }

    public void setSeatUtilizationPct(double seatUtilizationPct) {
        this.seatUtilizationPct = seatUtilizationPct;
    }

    public int getNoShowCount() {
        return noShowCount;
    }

    public void setNoShowCount(int noShowCount) {
        this.noShowCount = noShowCount;
    }

    public double getNoShowPct() {
        return noShowPct;
    }

    public void setNoShowPct(double noShowPct) {
        this.noShowPct = noShowPct;
    }

    public int getCancellationCount() {
        return cancellationCount;
    }

    public void setCancellationCount(int cancellationCount) {
        this.cancellationCount = cancellationCount;
    }

    public double getCancellationPct() {
        return cancellationPct;
    }

    public void setCancellationPct(double cancellationPct) {
        this.cancellationPct = cancellationPct;
    }

    public int getRepeatCustomerCount() {
        return repeatCustomerCount;
    }

    public void setRepeatCustomerCount(int repeatCustomerCount) {
        this.repeatCustomerCount = repeatCustomerCount;
    }

    public double getRepeatCustomerPct() {
        return repeatCustomerPct;
    }

    public void setRepeatCustomerPct(double repeatCustomerPct) {
        this.repeatCustomerPct = repeatCustomerPct;
    }

    public int getRecoveredCovers() {
        return recoveredCovers;
    }

    public void setRecoveredCovers(int recoveredCovers) {
        this.recoveredCovers = recoveredCovers;
    }

    public List<DailyCoverTrend> getDailyTrends() {
        return dailyTrends;
    }

    public void setDailyTrends(List<DailyCoverTrend> dailyTrends) {
        this.dailyTrends = dailyTrends != null ? dailyTrends : new ArrayList<>();
    }

    public List<SlotLoadMetric> getSlotMetrics() {
        return slotMetrics;
    }

    public void setSlotMetrics(List<SlotLoadMetric> slotMetrics) {
        this.slotMetrics = slotMetrics != null ? slotMetrics : new ArrayList<>();
    }

    public List<TablePerformanceMetric> getTableMetrics() {
        return tableMetrics;
    }

    public void setTableMetrics(List<TablePerformanceMetric> tableMetrics) {
        this.tableMetrics = tableMetrics != null ? tableMetrics : new ArrayList<>();
    }

    public List<TopGuestMetric> getTopGuests() {
        return topGuests;
    }

    public void setTopGuests(List<TopGuestMetric> topGuests) {
        this.topGuests = topGuests != null ? topGuests : new ArrayList<>();
    }

    public List<TagCountMetric> getTagDistribution() {
        return tagDistribution;
    }

    public void setTagDistribution(List<TagCountMetric> tagDistribution) {
        this.tagDistribution = tagDistribution != null ? tagDistribution : new ArrayList<>();
    }

    public List<WaitlistFunnelStep> getWaitlistFunnel() {
        return waitlistFunnel;
    }

    public void setWaitlistFunnel(List<WaitlistFunnelStep> waitlistFunnel) {
        this.waitlistFunnel = waitlistFunnel != null ? waitlistFunnel : new ArrayList<>();
    }

    public List<String> getKeyTakeaways() {
        return keyTakeaways;
    }

    public void setKeyTakeaways(List<String> keyTakeaways) {
        this.keyTakeaways = keyTakeaways != null ? keyTakeaways : new ArrayList<>();
    }
}
