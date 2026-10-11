package security;

import model.AppUser;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Thread-safe singleton implementation of AuthContext managing active desktop session state.
 */
public class Session implements AuthContext {
    private static final Session INSTANCE = new Session();
    private static volatile AppUser currentUser = null;
    private static final List<Consumer<AppUser>> sessionListeners = new CopyOnWriteArrayList<>();

    private Session() {
    }

    public static Session getInstance() {
        return INSTANCE;
    }

    @Override
    public AppUser currentUser() {
        return currentUser;
    }

    public static boolean isLoggedIn() {
        return currentUser != null;
    }

    public static synchronized void login(AppUser user) {
        currentUser = user;
        notifyListeners(user);
    }

    public static synchronized void logout() {
        currentUser = null;
        notifyListeners(null);
    }

    public static synchronized void refreshUser(AppUser updatedUser) {
        currentUser = updatedUser;
        notifyListeners(updatedUser);
    }

    public static void addSessionListener(Consumer<AppUser> listener) {
        sessionListeners.add(listener);
    }

    public static void removeSessionListener(Consumer<AppUser> listener) {
        sessionListeners.remove(listener);
    }

    private static void notifyListeners(AppUser user) {
        for (Consumer<AppUser> listener : sessionListeners) {
            try {
                listener.accept(user);
            } catch (Exception ignored) {
            }
        }
    }
}
