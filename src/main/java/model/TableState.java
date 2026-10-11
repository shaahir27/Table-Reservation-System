package model;

/**
 * Dynamic state of a table for a given date and time slot.
 */
public enum TableState {
    AVAILABLE("Available"),
    RESERVED("Reserved"),
    SEATED("Seated"),
    HELD("Held"),
    OVERDUE("Overdue"),
    MAINTENANCE("Maintenance");

    private final String displayName;

    TableState(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
