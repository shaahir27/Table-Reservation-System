package service;

import model.AvailabilityCell;
import model.AvailabilityGrid;
import model.TimeSlot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests verifying AvailabilityCell and AvailabilityGrid business logic (P5 Matrix).
 */
class AvailabilityGridTest {

    @Test
    @DisplayName("AvailabilityCell accurately calculates isFull and isFewLeft")
    void testCellThresholds() {
        LocalDate today = LocalDate.now();

        // 0 free tables -> Full
        AvailabilityCell fullCell = new AvailabilityCell(today, 1, "12:00 - 14:00", 0, 8, 3);
        assertTrue(fullCell.isFull(), "0 free tables should report isFull == true");
        assertFalse(fullCell.isFewLeft(), "0 free tables is full, not few left");

        // 1 free table out of 8 -> Few Left (<= 25% threshold)
        AvailabilityCell fewCell = new AvailabilityCell(today, 1, "12:00 - 14:00", 1, 8, 0);
        assertFalse(fewCell.isFull(), "1 free table is not full");
        assertTrue(fewCell.isFewLeft(), "1 table out of 8 is <= 25% threshold (few left)");

        // 2 free tables out of 8 -> Few Left (2 <= 2)
        AvailabilityCell fewCell2 = new AvailabilityCell(today, 1, "12:00 - 14:00", 2, 8, 0);
        assertTrue(fewCell2.isFewLeft(), "2 tables out of 8 is <= 25% threshold");

        // 5 free tables out of 8 -> Plentiful
        AvailabilityCell plentyCell = new AvailabilityCell(today, 1, "12:00 - 14:00", 5, 8, 0);
        assertFalse(plentyCell.isFull());
        assertFalse(plentyCell.isFewLeft());
    }

    @Test
    @DisplayName("AvailabilityGrid returns cell for matching date and slotId")
    void testGridLookup() {
        LocalDate d1 = LocalDate.of(2026, 10, 8);
        LocalDate d2 = LocalDate.of(2026, 10, 9);
        TimeSlot slot1 = new TimeSlot(1, "Lunch", LocalTime.of(12, 0), LocalTime.of(14, 0));

        Map<String, AvailabilityCell> map = new HashMap<>();
        AvailabilityCell c1 = new AvailabilityCell(d1, 1, "Lunch", 4, 8, 0);
        AvailabilityCell c2 = new AvailabilityCell(d2, 1, "Lunch", 0, 8, 2);
        map.put(d1.toString() + "_1", c1);
        map.put(d2.toString() + "_1", c2);

        AvailabilityGrid grid = new AvailabilityGrid(List.of(d1, d2), List.of(slot1), 2, null, 8, map);

        assertEquals(c1, grid.getCell(d1, 1));
        assertEquals(c2, grid.getCell(d2, 1));
        assertNull(grid.getCell(d1, 99));
        assertNull(grid.getCell(null, 1));
    }
}
