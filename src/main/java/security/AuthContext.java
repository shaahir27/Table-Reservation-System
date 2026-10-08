package security;

import exception.AccessDeniedException;
import model.AppUser;

/**
 * Authentication and authorization context interface.
 * Injected into services so business logic can be tested deterministically with AuthContext.fixed(user)
 * without depending on Swing UI state.
 */
public interface AuthContext {
    AppUser currentUser();

    default void require(Permission permission) {
        AppUser user = currentUser();
        if (user == null) {
            throw new AccessDeniedException("Authentication required. Please sign in.");
        }
        if (!user.getRole().can(permission)) {
            throw new AccessDeniedException("Access denied: Your role (" + user.getRole() + ") lacks permission " + permission + ".");
        }
    }

    default boolean hasPermission(Permission permission) {
        AppUser user = currentUser();
        return user != null && user.getRole().can(permission);
    }

    static AuthContext fixed(AppUser user) {
        return () -> user;
    }
}
