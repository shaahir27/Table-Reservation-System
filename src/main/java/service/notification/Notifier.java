package service.notification;

import model.Reservation;
import model.RestaurantTable;
import model.WaitlistEntry;

/**
 * Strategy interface for dispatching notifications when waitlist offers are created or expire.
 */
public interface Notifier {
    void offerCreated(WaitlistEntry entry, Reservation held, RestaurantTable table);
    void offerExpired(WaitlistEntry entry);
}
