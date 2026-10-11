package service.offer;

import model.RestaurantTable;
import model.WaitlistEntry;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Default OfferPolicy (BestFit):
 * Priority:
 *   1. Least wasted seats (capacity - partySize)
 *   2. Explicit section match wins ties over no preference
 *   3. FIFO (entry_id / creation order)
 * Hard filter:
 *   - partySize <= table.capacity
 *   - prefLocation is null or matches table.location
 *   - table.capacity - partySize <= maxWaste (prevents 10-top going to 2 guests)
 */
public class BestFitOfferPolicy implements OfferPolicy {

    @Override
    public Optional<WaitlistEntry> pick(RestaurantTable table, List<WaitlistEntry> candidates, int maxWaste) {
        if (table == null || candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }

        return candidates.stream()
                .filter(w -> w.getPartySize() <= table.getCapacity())
                .filter(w -> w.getPrefLocation() == null || w.getPrefLocation().isBlank()
                        || (table.getLocation() != null && w.getPrefLocation().equalsIgnoreCase(table.getLocation())))
                .filter(w -> (table.getCapacity() - w.getPartySize()) <= maxWaste)
                .min(Comparator.comparingInt((WaitlistEntry w) -> table.getCapacity() - w.getPartySize())
                        .thenComparingInt(w -> (w.getPrefLocation() != null && !w.getPrefLocation().isBlank()) ? 0 : 1)
                        .thenComparingInt(WaitlistEntry::getEntryId));
    }
}
