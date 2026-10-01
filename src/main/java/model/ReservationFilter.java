package model;

import java.time.LocalDate;

/**
 * Filter criteria for querying and searching reservation history.
 */
public class ReservationFilter {
    private String searchText;
    private LocalDate date;
    private ReservationStatus status;
    private String tableNumber;

    public ReservationFilter() {
    }

    public ReservationFilter(String searchText, LocalDate date, ReservationStatus status, String tableNumber) {
        this.searchText = searchText;
        this.date = date;
        this.status = status;
        this.tableNumber = tableNumber;
    }

    public String getSearchText() {
        return searchText;
    }

    public void setSearchText(String searchText) {
        this.searchText = searchText;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public void setStatus(ReservationStatus status) {
        this.status = status;
    }

    public String getTableNumber() {
        return tableNumber;
    }

    public void setTableNumber(String tableNumber) {
        this.tableNumber = tableNumber;
    }
}
