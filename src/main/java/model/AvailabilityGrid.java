package model;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Complete multi-day, multi-slot availability matrix data representation.
 */
public record AvailabilityGrid(
        List<LocalDate> dates,
        List<TimeSlot> slots,
        int guests,
        String section,
        int totalFittingTables,
        Map<String, AvailabilityCell> cells
) {
    public AvailabilityCell getCell(LocalDate date, int slotId) {
        if (cells == null || date == null) return null;
        return cells.get(date.toString() + "_" + slotId);
    }
}
