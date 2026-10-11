package model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Read-only DTO for joined reservation queries (P3 Lifecycle & Service Board).
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

    // V2 lifecycle fields
    private ReservationSource source;
    private LocalDateTime seatedAt;
    private LocalDateTime holdExpiresAt;
    private ReconfirmStatus reconfirmStatus;

    public ReservationDetail() {
        this.source = ReservationSource.BOOKING;
        this.reconfirmStatus = ReconfirmStatus.PENDING;
    }

    public ReservationDetail(int reservationId, String displayCode, String customerName, String phone, 
                             String tableNumber, LocalDate reservationDate, String slotLabel, 
                             int guestCount, ReservationStatus status, LocalDateTime createdAt) {
        this(reservationId, displayCode, customerName, phone, tableNumber, reservationDate, slotLabel,
                guestCount, status, createdAt, ReservationSource.BOOKING, null, null, ReconfirmStatus.PENDING);
    }

    public ReservationDetail(int reservationId, String displayCode, String customerName, String phone, 
                             String tableNumber, LocalDate reservationDate, String slotLabel, 
                             int guestCount, ReservationStatus status, LocalDateTime createdAt,
                             ReservationSource source, LocalDateTime seatedAt, LocalDateTime holdExpiresAt, ReconfirmStatus reconfirmStatus) {
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
        this.source = source != null ? source : ReservationSource.BOOKING;
        this.seatedAt = seatedAt;
        this.holdExpiresAt = holdExpiresAt;
        this.reconfirmStatus = reconfirmStatus != null ? reconfirmStatus : ReconfirmStatus.PENDING;
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

    @Override
    public String toString() {
        return getDisplayCode() + " - " + customerName + " (" + tableNumber + ", " + slotLabel + ", " + status + ")";
    }
}
