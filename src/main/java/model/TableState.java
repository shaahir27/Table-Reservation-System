package model;

/**
 * Dynamic state of a table for a given date and time slot.
 */
public enum TableState {
    AVAILABLE,
    RESERVED,
    MAINTENANCE
}
