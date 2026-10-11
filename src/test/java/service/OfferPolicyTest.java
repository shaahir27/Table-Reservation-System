package service;

import model.RestaurantTable;
import model.TableStatus;
import model.WaitlistEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import service.offer.BestFitOfferPolicy;
import service.offer.FifoOfferPolicy;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests verifying OfferPolicy strategies (BestFit vs FIFO) for Slot Recovery Refill (P4).
 */
class OfferPolicyTest {
    private final BestFitOfferPolicy bestFitPolicy = new BestFitOfferPolicy();
    private final FifoOfferPolicy fifoPolicy = new FifoOfferPolicy();

    private WaitlistEntry createEntry(int entryId, String name, int partySize, String prefLocation) {
        WaitlistEntry entry = new WaitlistEntry();
        entry.setEntryId(entryId);
        entry.setCustomerId(entryId * 10);
        entry.setCustomerName(name);
        entry.setPartySize(partySize);
        entry.setPreferredSection(prefLocation);
        entry.setServiceDate(LocalDate.now());
        return entry;
    }

    @Test
    @DisplayName("BestFit picks party with least wasted seats (smallest capacity difference)")
    void testBestFit_LeastWaste() {
        RestaurantTable table = new RestaurantTable(1, "T03", 4, "Indoor", TableStatus.AVAILABLE);

        WaitlistEntry e1 = createEntry(1, "Alice (Party 2)", 2, null);
        WaitlistEntry e2 = createEntry(2, "Bob (Party 4)", 4, null);
        WaitlistEntry e3 = createEntry(3, "Charlie (Party 3)", 3, null);

        // On a 4-top, party of 4 has waste 0, party of 3 has waste 1, party of 2 has waste 2
        Optional<WaitlistEntry> pick = bestFitPolicy.pick(table, List.of(e1, e2, e3), 2);
        assertTrue(pick.isPresent());
        assertEquals("Bob (Party 4)", pick.get().getCustomerName());
    }

    @Test
    @DisplayName("BestFit breaks tie on equal waste by giving priority to explicit section preference")
    void testBestFit_ExplicitSectionWinsTie() {
        RestaurantTable table = new RestaurantTable(1, "T05", 4, "Window", TableStatus.AVAILABLE);

        WaitlistEntry e1 = createEntry(1, "Alice (No Pref)", 4, null);
        WaitlistEntry e2 = createEntry(2, "Bob (Window Pref)", 4, "Window");

        Optional<WaitlistEntry> pick = bestFitPolicy.pick(table, List.of(e1, e2), 2);
        assertTrue(pick.isPresent());
        assertEquals("Bob (Window Pref)", pick.get().getCustomerName());
    }

    @Test
    @DisplayName("BestFit breaks tie on equal waste and preference using FIFO (earliest entry ID)")
    void testBestFit_FifoTieBreaking() {
        RestaurantTable table = new RestaurantTable(1, "T02", 4, "Indoor", TableStatus.AVAILABLE);

        WaitlistEntry e1 = createEntry(10, "Alice", 4, null);
        WaitlistEntry e2 = createEntry(15, "Bob", 4, null);

        Optional<WaitlistEntry> pick = bestFitPolicy.pick(table, List.of(e2, e1), 2);
        assertTrue(pick.isPresent());
        assertEquals(10, pick.get().getEntryId());
        assertEquals("Alice", pick.get().getCustomerName());
    }

    @Test
    @DisplayName("BestFit filters out candidate if wasted seats exceed maxWaste (e.g. 10-top to party of 2)")
    void testBestFit_WasteCapEnforced() {
        RestaurantTable bigTable = new RestaurantTable(1, "T09", 10, "Hall", TableStatus.AVAILABLE);

        WaitlistEntry e1 = createEntry(1, "Couple", 2, null); // waste = 8

        // Max waste is 2 seats
        Optional<WaitlistEntry> pick = bestFitPolicy.pick(bigTable, List.of(e1), 2);
        assertTrue(pick.isEmpty(), "Party of 2 should not be auto-offered a 10-top when maxWaste is 2");

        // But with maxWaste 8, it should accept
        Optional<WaitlistEntry> pickWithRelaxedWaste = bestFitPolicy.pick(bigTable, List.of(e1), 8);
        assertTrue(pickWithRelaxedWaste.isPresent());
        assertEquals("Couple", pickWithRelaxedWaste.get().getCustomerName());
    }

    @Test
    @DisplayName("BestFit rejects candidate whose party size exceeds table capacity")
    void testBestFit_PartyTooLarge() {
        RestaurantTable smallTable = new RestaurantTable(1, "T01", 2, "Indoor", TableStatus.AVAILABLE);

        WaitlistEntry e1 = createEntry(1, "Family", 4, null);

        Optional<WaitlistEntry> pick = bestFitPolicy.pick(smallTable, List.of(e1), 2);
        assertTrue(pick.isEmpty());
    }

    @Test
    @DisplayName("FifoOfferPolicy always picks earliest eligible candidate regardless of waste")
    void testFifoPolicy_FirstInLineWins() {
        RestaurantTable table = new RestaurantTable(1, "T03", 4, "Indoor", TableStatus.AVAILABLE);

        WaitlistEntry e1 = createEntry(1, "Early Couple", 2, null);
        WaitlistEntry e2 = createEntry(2, "Late Four", 4, null);

        // Even though e2 fits the 4-top perfectly with 0 waste, FIFO policy picks e1 (first in queue)
        Optional<WaitlistEntry> pick = fifoPolicy.pick(table, List.of(e1, e2), 2);
        assertTrue(pick.isPresent());
        assertEquals("Early Couple", pick.get().getCustomerName());
    }
}
