package model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Domain entity representing a customer on the dining waitlist queue.
 */
public class WaitlistEntry {
    private int entryId;
    private int customerId;
    private String customerName;
    private String customerPhone;
    private LocalDate serviceDate;
    private Integer slotId; // null = any slot that day
    private String slotLabel;
    private int partySize;
    private String prefLocation;
    private WaitlistStatus status;
    private Integer offeredReservationId;
    private Integer createdBy;
    private LocalDateTime createdAt;
    private String note;

    public WaitlistEntry() {
        this.status = WaitlistStatus.WAITING;
    }

    public WaitlistEntry(int entryId, int customerId, String customerName, String customerPhone,
                         LocalDate serviceDate, Integer slotId, String slotLabel,
                         int partySize, String prefLocation, WaitlistStatus status,
                         Integer offeredReservationId, Integer createdBy, LocalDateTime createdAt, String note) {
        this.entryId = entryId;
        this.customerId = customerId;
        this.customerName = customerName;
        this.customerPhone = customerPhone;
        this.serviceDate = serviceDate;
        this.slotId = slotId;
        this.slotLabel = slotLabel;
        this.partySize = partySize;
        this.prefLocation = prefLocation;
        this.status = status != null ? status : WaitlistStatus.WAITING;
        this.offeredReservationId = offeredReservationId;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.note = note;
    }

    public int getEntryId() {
        return entryId;
    }

    public void setEntryId(int entryId) {
        this.entryId = entryId;
    }

    public int getCustomerId() {
        return customerId;
    }

    public void setCustomerId(int customerId) {
        this.customerId = customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public LocalDate getServiceDate() {
        return serviceDate;
    }

    public void setServiceDate(LocalDate serviceDate) {
        this.serviceDate = serviceDate;
    }

    public Integer getSlotId() {
        return slotId;
    }

    public void setSlotId(Integer slotId) {
        this.slotId = slotId;
    }

    public String getSlotLabel() {
        return slotLabel;
    }

    public void setSlotLabel(String slotLabel) {
        this.slotLabel = slotLabel;
    }

    public int getPartySize() {
        return partySize;
    }

    public void setPartySize(int partySize) {
        this.partySize = partySize;
    }

    public String getPrefLocation() {
        return prefLocation;
    }

    public void setPrefLocation(String prefLocation) {
        this.prefLocation = prefLocation;
    }

    public WaitlistStatus getStatus() {
        return status;
    }

    public void setStatus(WaitlistStatus status) {
        this.status = status;
    }

    public Integer getOfferedReservationId() {
        return offeredReservationId;
    }

    public void setOfferedReservationId(Integer offeredReservationId) {
        this.offeredReservationId = offeredReservationId;
    }

    public Integer getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Integer createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getPhone() {
        return customerPhone;
    }

    public void setPhone(String phone) {
        this.customerPhone = phone;
    }

    public String getPreferredSection() {
        return prefLocation;
    }

    public void setPreferredSection(String prefLocation) {
        this.prefLocation = prefLocation;
    }

    public String getNotes() {
        return note;
    }

    public void setNotes(String notes) {
        this.note = notes;
    }

    public String getDisplaySlot() {
        return slotLabel != null && !slotLabel.isBlank() ? slotLabel : "Any Slot";
    }

    @Override
    public String toString() {
        return "WL#" + entryId + " [" + customerName + ", " + partySize + "p, " + getDisplaySlot() + ", " + status + "]";
    }
}
