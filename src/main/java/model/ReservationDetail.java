package model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Read-only DTO for joined reservation queries.
 */
public class ReservationDetail {
    private int reservationId;
    private String displayCode;
    private String customerName;
    private String phone;
    private String tableNumber;
    private LocalDate reservationDate;
    private String slotLabel;
    private int guestCount;
    private ReservationStatus status;
    private LocalDateTime createdAt;

    public ReservationDetail() {
    }

    public ReservationDetail(int reservationId, String displayCode, String customerName, String phone, 
                             String tableNumber, LocalDate reservationDate, String slotLabel, 
                             int guestCount, ReservationStatus status, LocalDateTime createdAt) {
        this.reservationId = reservationId;
        this.displayCode = displayCode != null ? displayCode : "RES" + reservationId;
        this.customerName = customerName;
        this.phone = phone;
        this.tableNumber = tableNumber;
        this.reservationDate = reservationDate;
        this.slotLabel = slotLabel;
        this.guestCount = guestCount;
        this.status = status;
        this.createdAt = createdAt;
    }

    public int getReservationId() {
        return reservationId;
    }

    public void setReservationId(int reservationId) {
        this.reservationId = reservationId;
        this.displayCode = "RES" + reservationId;
    }

    public String getDisplayCode() {
        return displayCode != null ? displayCode : "RES" + reservationId;
    }

    public void setDisplayCode(String displayCode) {
        this.displayCode = displayCode;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getTableNumber() {
        return tableNumber;
    }

    public void setTableNumber(String tableNumber) {
        this.tableNumber = tableNumber;
    }

    public LocalDate getReservationDate() {
        return reservationDate;
    }

    public void setReservationDate(LocalDate reservationDate) {
        this.reservationDate = reservationDate;
    }

    public String getSlotLabel() {
        return slotLabel;
    }

    public void setSlotLabel(String slotLabel) {
        this.slotLabel = slotLabel;
    }

    public int getGuestCount() {
        return guestCount;
    }

    public void setGuestCount(int guestCount) {
        this.guestCount = guestCount;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public void setStatus(ReservationStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return getDisplayCode() + " - " + customerName + " (" + tableNumber + ", " + slotLabel + ")";
    }
}
