# Project Assumptions & Design Decisions

This document records all architectural and operational assumptions made during the implementation of the Restaurant Table Reservation System (RTRS), per Section 0 of `IMPLEMENTATION.md`.

## 1. Environment & Database
- **Oracle Version:** The SQL scripts and JDBC connections are compatible with Oracle 11g XE, 18c XE, and 21c XE.
- **Connection Configuration:** Externalized in `config.properties` in the working directory (with classpath fallback). No hardcoded credentials exist in source code.
- **Schema Management:** Database schema creation and initial seeding are performed by the database administrator / user using the provided SQL scripts in `sql/`.

## 2. Business Rules & Models
- **Fixed Seatings:** 5 fixed 90-minute time slots are used rather than continuous 30-minute intervals to avoid physical table overlap anomalies while preserving 3NF referential integrity.
- **Table Status vs. Availability:** `restaurant_table.status` represents physical, permanent operational status (`AVAILABLE`, `MAINTENANCE`, `INACTIVE`). Temporal availability is calculated dynamically by querying non-cancelled reservations for a specific date and time slot.
- **Double Booking Guarantee:** The database engine provides the ultimate guarantee through a function-based unique index (`UQ_ACTIVE_BOOKING`). Java application locking (`SELECT ... FOR UPDATE`) provides serialization and friendly pre-check error messages.
- **Cancellation & History:** Reservations are never deleted from the database. Cancellations set `status = 'CANCELLED'`. Because cancelled records evaluate to NULL in the unique index, the slot becomes immediately rebookable while preserving audit history.
- **Customer Phone Format:** Standard 10-digit Indian mobile format (digits 6-9 followed by 9 digits). Normalization strips spaces, dashes, leading `0`, and `+91`.
- **Table Capacity:** Restricted to between 1 and 20 seats.

## 3. UI/UX Design System
- **Look and Feel:** Uses FlatLaf Light with Inter font.
- **Threading:** Swing Event Dispatch Thread (EDT) is never blocked by database calls. All I/O operations are offloaded using the `Ui.async` worker pattern.
- **Feedback:** Success operations trigger subtle 2.5-second non-blocking toasts. Destructive actions and booking confirmations use modal dialogs.
