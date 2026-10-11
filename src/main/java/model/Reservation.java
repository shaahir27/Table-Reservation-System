package model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Domain entity representing a customer reservation (P3 Lifecycle).
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

    // V2 lifecycle fields (Migration B)
    private ReservationSource source;
    private LocalDateTime seatedAt;
    private LocalDateTime holdExpiresAt;
    private ReconfirmStatus reconfirmStatus;

    public Reservation() {
        this.status = ReservationStatus.CONFIRMED;
        this.source = ReservationSource.BOOKING;
        this.reconfirmStatus = ReconfirmStatus.PENDING;
    }

    public Reservation(int reservationId, int customerId, int tableId, LocalDate reservationDate, 
                       int slotId, int guestCount, ReservationStatus status, LocalDateTime createdAt) {
        this(reservationId, customerId, tableId, reservationDate, slotId, guestCount, status, createdAt, null,
                ReservationSource.BOOKING, null, null, ReconfirmStatus.PENDING);
    }

    public Reservation(int reservationId, int customerId, int tableId, LocalDate reservationDate, 
                       int slotId, int guestCount, ReservationStatus status, LocalDateTime createdAt, Integer createdBy) {
        this(reservationId, customerId, tableId, reservationDate, slotId, guestCount, status, createdAt, createdBy,
                ReservationSource.BOOKING, null, null, ReconfirmStatus.PENDING);
    }

    public Reservation(int reservationId, int customerId, int tableId, LocalDate reservationDate, 
                       int slotId, int guestCount, ReservationStatus status, LocalDateTime createdAt, Integer createdBy,
                       ReservationSource source, LocalDateTime seatedAt, LocalDateTime holdExpiresAt, ReconfirmStatus reconfirmStatus) {
        this.reservationId = reservationId;
        this.customerId = customerId;
        this.tableId = tableId;
        this.reservationDate = reservationDate;
        this.slotId = slotId;
        this.guestCount = guestCount;
        this.status = status != null ? status : ReservationStatus.CONFIRMED;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        this.source = source != null ? source : ReservationSource.BOOKING;
        this.seatedAt = seatedAt;
        this.holdExpiresAt = holdExpiresAt;
        this.reconfirmStatus = reconfirmStatus != null ? reconfirmStatus : ReconfirmStatus.PENDING;
    }

    public Reservation(int customerId, int tableId, LocalDate reservationDate, int slotId, int guestCount) {
        this(0, customerId, tableId, reservationDate, slotId, guestCount, ReservationStatus.CONFIRMED,
                util.DbClock.localDateTime(), null, ReservationSource.BOOKING, null, null, ReconfirmStatus.PENDING);
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

    public ReservationSource getSource() {
        return source;
    }

    public void setSource(ReservationSource source) {
        this.source = source;
    }

    public LocalDateTime getSeatedAt() {
        return seatedAt;
    }

    public void setSeatedAt(LocalDateTime seatedAt) {
        this.seatedAt = seatedAt;
    }

    public LocalDateTime getHoldExpiresAt() {
        return holdExpiresAt;
    }

    public void setHoldExpiresAt(LocalDateTime holdExpiresAt) {
        this.holdExpiresAt = holdExpiresAt;
    }

    public ReconfirmStatus getReconfirmStatus() {
        return reconfirmStatus;
    }

    public void setReconfirmStatus(ReconfirmStatus reconfirmStatus) {
        this.reconfirmStatus = reconfirmStatus;
    }

    public String getDisplayCode() {
        return "RES" + reservationId;
    }

    /**
     * Evaluates if a booking is currently in an OVERDUE derived state.
     * CONFIRMED and today and current time >= slot.start + graceMinutes.
     */
    public boolean isOverdue(LocalTime slotStartTime, int graceMinutes) {
        if (status != ReservationStatus.CONFIRMED) return false;
        LocalDate today = util.DbClock.today();
        if (!reservationDate.equals(today)) return false;
        LocalTime now = util.DbClock.localTime();
        LocalTime cutoff = slotStartTime.plusMinutes(graceMinutes);
        return now.isAfter(cutoff);
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
        return getDisplayCode() + " (" + reservationDate + ", " + status + ", " + source + ")";
    }
}
