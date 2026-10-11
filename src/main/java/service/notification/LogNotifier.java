package service.notification;

import model.Reservation;
import model.RestaurantTable;
import model.WaitlistEntry;
import util.AppLog;

/**
 * Default logger-based notifier.
 */
public class LogNotifier implements Notifier {

    @Override
    public void offerCreated(WaitlistEntry entry, Reservation held, RestaurantTable table) {
        AppLog.info("WAITLIST OFFER CREATED: " + entry.getCustomerName() + " (" + entry.getPartySize() + "p) -> Table "
                + table.getTableNumber() + " (Res #" + held.getReservationId() + ", hold expires " + held.getHoldExpiresAt() + ")");
    }

    @Override
    public void offerExpired(WaitlistEntry entry) {
        AppLog.info("WAITLIST OFFER EXPIRED: Entry #" + entry.getEntryId() + " (" + entry.getCustomerName() + ")");
    }
}
