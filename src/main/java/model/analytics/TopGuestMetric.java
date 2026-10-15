package model.analytics;

import java.time.LocalDate;

/**
 * CRM guest loyalty metric tracking frequent diners and VIPs.
 */
public class TopGuestMetric {
    private final int customerId;
    private final String name;
    private final String phone;
    private final String tags;
    private final int visitCount;
    private final int totalCovers;
    private final LocalDate lastVisit;

    public TopGuestMetric(int customerId, String name, String phone, String tags,
                          int visitCount, int totalCovers, LocalDate lastVisit) {
        this.customerId = customerId;
        this.name = name;
        this.phone = phone;
        this.tags = tags != null ? tags : "";
        this.visitCount = visitCount;
        this.totalCovers = totalCovers;
        this.lastVisit = lastVisit;
    }

    public int getCustomerId() {
        return customerId;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public String getTags() {
        return tags;
    }

    public int getVisitCount() {
        return visitCount;
    }

    public int getTotalCovers() {
        return totalCovers;
    }

    public LocalDate getLastVisit() {
        return lastVisit;
    }
}
