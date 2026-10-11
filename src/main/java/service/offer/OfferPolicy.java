package service.offer;

import model.RestaurantTable;
import model.WaitlistEntry;

import java.util.List;
import java.util.Optional;

/**
 * Strategy interface for selecting the best waitlist candidate to receive an offer for a freed table.
 */
public interface OfferPolicy {
    Optional<WaitlistEntry> pick(RestaurantTable table, List<WaitlistEntry> candidates, int maxWaste);
}
