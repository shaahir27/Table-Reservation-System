package model;

/**
 * Model representing table status, reservation state, and recommendation flag for a specific slot.
 */
public class TableAvailability {
    private RestaurantTable table;
    private TableState state;
    private boolean recommended;
    private ReservationDetail activeReservation;

    public TableAvailability() {
        this.state = TableState.AVAILABLE;
    }

    public TableAvailability(RestaurantTable table, TableState state) {
        this(table, state, false, null);
    }

    public TableAvailability(RestaurantTable table, TableState state, boolean recommended) {
        this(table, state, recommended, null);
    }

    public TableAvailability(RestaurantTable table, TableState state, boolean recommended, ReservationDetail activeReservation) {
        this.table = table;
        this.state = state;
        this.recommended = recommended;
        this.activeReservation = activeReservation;
    }

    public ReservationDetail getActiveReservation() {
        return activeReservation;
    }

    public void setActiveReservation(ReservationDetail activeReservation) {
        this.activeReservation = activeReservation;
    }

    public RestaurantTable getTable() {
        return table;
    }

    public void setTable(RestaurantTable table) {
        this.table = table;
    }

    public TableState getState() {
        return state;
    }

    public void setState(TableState state) {
        this.state = state;
    }

    public boolean isRecommended() {
        return recommended;
    }

    public void setRecommended(boolean recommended) {
        this.recommended = recommended;
    }

    @Override
    public String toString() {
        return (table != null ? table.getTableNumber() : "Unknown") + " [" + state + (recommended ? ", RECOMMENDED" : "") + "]";
    }
}
