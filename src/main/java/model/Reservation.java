package model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Domain entity representing a customer reservation.
 */
public class Reservation {
    private int reservationId;
    private int customerId;
    private int tableId;
    private LocalDate reservationDate;
    private int slotId;
    private int guestCount;
    private ReservationStatus status;
    private LocalDateTime createdAt;
    private Integer createdBy;

    public Reservation() {
        this.status = ReservationStatus.CONFIRMED;
    }

    public Reservation(int reservationId, int customerId, int tableId, LocalDate reservationDate, 
                       int slotId, int guestCount, ReservationStatus status, LocalDateTime createdAt) {
        this(reservationId, customerId, tableId, reservationDate, slotId, guestCount, status, createdAt, null);
    }

    public Reservation(int reservationId, int customerId, int tableId, LocalDate reservationDate, 
                       int slotId, int guestCount, ReservationStatus status, LocalDateTime createdAt, Integer createdBy) {
        this.reservationId = reservationId;
        this.customerId = customerId;
        this.tableId = tableId;
        this.reservationDate = reservationDate;
        this.slotId = slotId;
        this.guestCount = guestCount;
        this.status = status != null ? status : ReservationStatus.CONFIRMED;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
    }

    public Reservation(int customerId, int tableId, LocalDate reservationDate, int slotId, int guestCount) {
        this(0, customerId, tableId, reservationDate, slotId, guestCount, ReservationStatus.CONFIRMED, util.DbClock.localDateTime(), null);
    }

    public int getReservationId() {
        return reservationId;
    }

    public void setReservationId(int reservationId) {
        this.reservationId = reservationId;
    }

    public int getCustomerId() {
        return customerId;
    }

    public void setCustomerId(int customerId) {
        this.customerId = customerId;
    }

    public int getTableId() {
        return tableId;
    }

    public void setTableId(int tableId) {
        this.tableId = tableId;
    }

    public LocalDate getReservationDate() {
        return reservationDate;
    }

    public void setReservationDate(LocalDate reservationDate) {
        this.reservationDate = reservationDate;
    }

    public int getSlotId() {
        return slotId;
    }

    public void setSlotId(int slotId) {
        this.slotId = slotId;
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

    public Integer getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Integer createdBy) {
        this.createdBy = createdBy;
    }

    public String getDisplayCode() {
        return "RES" + reservationId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Reservation that = (Reservation) o;
        return reservationId == that.reservationId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(reservationId);
    }

    @Override
    public String toString() {
        return getDisplayCode() + " (" + reservationDate + ", " + status + ")";
    }
}
