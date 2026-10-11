package service.notification;

import model.Reservation;
import model.RestaurantTable;
import model.WaitlistEntry;
import util.AppLog;

import javax.swing.SwingUtilities;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * UI notification dispatcher for the Slot Recovery Loop (P4).
 * Delivers toast/badge updates on EDT when waitlist offers are generated or lapse.
 */
public class UiNotifier implements Notifier {

    public interface OfferListener {
        void onOfferCreated(WaitlistEntry entry, Reservation held, RestaurantTable table);
        void onOfferExpired(WaitlistEntry entry);
    }

    private final List<OfferListener> listeners = new CopyOnWriteArrayList<>();

    public void addListener(OfferListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(OfferListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void offerCreated(WaitlistEntry entry, Reservation held, RestaurantTable table) {
        AppLog.info("NOTIFICATION: Waitlist offer created for " + entry.getCustomerName() + 
                " on Table " + (table != null ? table.getTableNumber() : "?"));
        SwingUtilities.invokeLater(() -> {
            for (OfferListener l : listeners) {
                try {
                    l.onOfferCreated(entry, held, table);
                } catch (Exception e) {
                    AppLog.warning("Error notifying OfferListener: " + e.getMessage());
                }
            }
        });
    }

    @Override
    public void offerExpired(WaitlistEntry entry) {
        AppLog.info("NOTIFICATION: Waitlist offer expired for " + entry.getCustomerName());
        SwingUtilities.invokeLater(() -> {
            for (OfferListener l : listeners) {
                try {
                    l.onOfferExpired(entry);
                } catch (Exception e) {
                    AppLog.warning("Error notifying OfferListener: " + e.getMessage());
                }
            }
        });
    }
}
