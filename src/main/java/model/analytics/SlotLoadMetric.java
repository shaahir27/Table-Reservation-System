package model.analytics;

/**
 * Aggregated dining metrics for a specific 90-minute time slot.
 */
public class SlotLoadMetric {
    private final int slotId;
    private final String slotLabel;
    private final String startTime;
    private final int totalBookings;
    private final int totalCovers;
    private final double capacityUtilisationPct;

    public SlotLoadMetric(int slotId, String slotLabel, String startTime, int totalBookings, int totalCovers, double capacityUtilisationPct) {
        this.slotId = slotId;
        this.slotLabel = slotLabel;
        this.startTime = startTime;
        this.totalBookings = totalBookings;
        this.totalCovers = totalCovers;
        this.capacityUtilisationPct = capacityUtilisationPct;
    }

    public int getSlotId() {
        return slotId;
    }

    public String getSlotLabel() {
        return slotLabel;
    }

    public String getStartTime() {
        return startTime;
    }

    public int getTotalBookings() {
        return totalBookings;
    }

    public int getTotalCovers() {
        return totalCovers;
    }

    public double getCapacityUtilisationPct() {
        return capacityUtilisationPct;
    }
}
