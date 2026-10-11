package model;

import java.time.LocalDate;

/**
 * Single cell in the One-Glance Availability Matrix representing a (date, time_slot) pair.
 */
public record AvailabilityCell(
        LocalDate date,
        int slotId,
        String slotLabel,
        int freeTables,
        int totalFittingTables,
        int waitingCount
) {
    public boolean isFull() {
        return freeTables == 0;
    }

    public boolean isFewLeft() {
        if (freeTables == 0) return false;
        int threshold = Math.max(1, (int) Math.ceil(totalFittingTables * 0.25));
        return freeTables <= threshold;
    }
}
