package model.analytics;

/**
 * Performance, turn rate, and seat efficiency metric for an individual table.
 */
public class TablePerformanceMetric {
    private final int tableId;
    private final String tableNumber;
    private final int capacity;
    private final String location;
    private final int turns;
    private final int totalCovers;
    private final double avgPartySize;
    private final double seatEfficiencyPct;

    public TablePerformanceMetric(int tableId, String tableNumber, int capacity, String location,
                                  int turns, int totalCovers, double avgPartySize, double seatEfficiencyPct) {
        this.tableId = tableId;
        this.tableNumber = tableNumber;
        this.capacity = capacity;
        this.location = location;
        this.turns = turns;
        this.totalCovers = totalCovers;
        this.avgPartySize = avgPartySize;
        this.seatEfficiencyPct = seatEfficiencyPct;
    }

    public int getTableId() {
        return tableId;
    }

    public String getTableNumber() {
        return tableNumber;
    }

    public int getCapacity() {
        return capacity;
    }

    public String getLocation() {
        return location;
    }

    public int getTurns() {
        return turns;
    }

    public int getTotalCovers() {
        return totalCovers;
    }

    public double getAvgPartySize() {
        return avgPartySize;
    }

    public double getSeatEfficiencyPct() {
        return seatEfficiencyPct;
    }
}
