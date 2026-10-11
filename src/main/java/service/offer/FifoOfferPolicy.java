package service.offer;

import model.RestaurantTable;
import model.WaitlistEntry;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Pure FIFO OfferPolicy for baseline comparison.
 */
public class FifoOfferPolicy implements OfferPolicy {

    @Override
    public Optional<WaitlistEntry> pick(RestaurantTable table, List<WaitlistEntry> candidates, int maxWaste) {
        if (table == null || candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }

        return candidates.stream()
                .filter(w -> w.getPartySize() <= table.getCapacity())
                .filter(w -> w.getPrefLocation() == null || w.getPrefLocation().isBlank()
                        || (table.getLocation() != null && w.getPrefLocation().equalsIgnoreCase(table.getLocation())))
                .min(Comparator.comparingInt(WaitlistEntry::getEntryId));
    }
}
