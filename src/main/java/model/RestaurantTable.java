package model;

import java.util.Objects;

/**
 * Domain entity representing a restaurant table.
 */
public class RestaurantTable {
    private int tableId;
    private String tableNumber;
    private int capacity;
    private String location;
    private TableStatus status;

    public RestaurantTable() {
        this.status = TableStatus.AVAILABLE;
    }

    public RestaurantTable(int tableId, String tableNumber, int capacity, String location, TableStatus status) {
        this.tableId = tableId;
        this.tableNumber = tableNumber;
        this.capacity = capacity;
        this.location = location;
        this.status = status;
    }

    public RestaurantTable(String tableNumber, int capacity, String location, TableStatus status) {
        this(0, tableNumber, capacity, location, status);
    }

    public int getTableId() {
        return tableId;
    }

    public void setTableId(int tableId) {
        this.tableId = tableId;
    }

    public String getTableNumber() {
        return tableNumber;
    }

    public void setTableNumber(String tableNumber) {
        this.tableNumber = tableNumber;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public TableStatus getStatus() {
        return status;
    }

    public void setStatus(TableStatus status) {
        this.status = status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RestaurantTable that = (RestaurantTable) o;
        return tableId == that.tableId &&
                Objects.equals(tableNumber, that.tableNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tableId, tableNumber);
    }

    @Override
    public String toString() {
        return tableNumber + " (" + capacity + " seats, " + location + ")";
    }
}
