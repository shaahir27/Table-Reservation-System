# Database Normalization Derivation & Justification

This document provides the theoretical derivation and practical justification for the database design of the **Restaurant Table Reservation System (RTRS)**, fulfilling the prerequisite requirements of Project 13.

---

## 1. Initial Unnormalized Representation (UNF / Flat Table)

Consider an unnormalized, flat spreadsheet or single-table representation storing all dining and reservation records:

```text
RESERVATION_FLAT(
    reservation_id,
    customer_name,
    phone,
    email,
    table_number,
    capacity,
    location,
    table_status,
    reservation_date,
    slot_label,
    slot_start_time,
    slot_end_time,
    guest_count,
    reservation_status,
    created_at
)
```

---

## 2. Functional Dependencies (FDs)

Analyzing the domain rules and real-world business constraints reveals the following functional dependencies:

1. **Primary Key Dependency:**  
   $$\text{reservation\_id} \rightarrow \text{all attributes}$$
2. **Customer Entity Determinant:**  
   $$\text{phone} \rightarrow \text{customer\_name}, \text{email}$$  
   *(Each customer is uniquely identified by their verified mobile number).*
3. **Restaurant Table Entity Determinant:**  
   $$\text{table\_number} \rightarrow \text{capacity}, \text{location}, \text{table\_status}$$  
   *(A physical dining table has fixed seating capacity, architectural location, and operational maintenance status).*
4. **Time Slot Entity Determinant:**  
   $$\text{slot\_label} \rightarrow \text{slot\_start\_time}, \text{slot\_end\_time}$$  
   *(Each standard seating window has deterministic start and end times).*
5. **Natural Candidate Key for Active Bookings:**  
   $$(\text{table\_number}, \text{reservation\_date}, \text{slot\_label}) \rightarrow \text{reservation\_id}, \text{customer\_phone}, \text{guest\_count}, \text{status}$$

---

## 3. Step-by-Step Normalization

### Step 1: First Normal Form (1NF)

**Definition:** A relation is in 1NF if and only if all attribute domains contain only atomic (indivisible) values and there are no repeating groups or arrays.

- In `RESERVATION_FLAT`, each column stores a single scalar value (e.g., individual guest counts, singular dates, individual phones rather than multi-valued lists).
- Multi-party reservations are recorded as individual rows rather than arrays of time intervals or guest names.
- A primary identifier is chosen (`reservation_id`).

**Status:** The relation satisfies **1NF**.

---

### Step 2: Second Normal Form (2NF)

**Definition:** A relation is in 2NF if it is in 1NF and no non-prime attribute is functionally dependent on a proper subset of any candidate key (**no partial dependencies**).

*Analysis under the natural composite key:*  
If we examine the natural composite candidate key:  
$$\{\text{phone}, \text{table\_number}, \text{reservation\_date}, \text{slot\_label}\}$$

- $\text{customer\_name}$ and $\text{email}$ depend solely on part of the candidate key: $\text{phone}$.
- $\text{capacity}$, $\text{location}$, and $\text{table\_status}$ depend solely on part of the candidate key: $\text{table\_number}$.
- $\text{slot\_start\_time}$ and $\text{slot\_end\_time}$ depend solely on $\text{slot\_label}$.

These represent blatant **partial dependencies**.

*Decomposition into 2NF:*  
To eliminate partial dependencies, we decompose the relation into distinct relations:
- `CUSTOMER(customer_id, customer_name, phone, email)`
- `RESTAURANT_TABLE(table_id, table_number, capacity, location, status)`
- `TIME_SLOT(slot_id, slot_label, start_time, end_time)`
- `RESERVATION(reservation_id, customer_id, table_id, slot_id, reservation_date, guest_count, status, created_at)`

---

### Step 3: Third Normal Form (3NF)

**Definition:** A relation is in 3NF if it is in 2NF and there are **no transitive dependencies** of non-prime attributes on candidate keys ($X \rightarrow Y$ and $Y \rightarrow Z$ where $Z$ is non-prime and $Y$ is not a superkey).

*Analysis of Decomposition:*
- In `CUSTOMER`: The primary key is `customer_id` (and alternate key `phone`). `customer_name` and `email` depend directly on `customer_id`. There are no intermediate transitive attributes.
- In `RESTAURANT_TABLE`: The primary key is `table_id` (and alternate key `table_number`). Attributes `capacity`, `location`, and `status` depend directly on `table_id`.
- In `TIME_SLOT`: `slot_id` determines `slot_label`, `start_time`, `end_time`.
- In `RESERVATION`: Non-prime attributes (`reservation_date`, `guest_count`, `status`, `created_at`) depend solely on `reservation_id`. Foreign keys (`customer_id`, `table_id`, `slot_id`) link to external entities without duplicating their attributes (e.g., table capacity or customer name are not redundantly duplicated in `RESERVATION`).

**Status:** The schema satisfies **3NF**.

---

### Step 4: Boyce-Codd Normal Form (BCNF)

**Definition:** A relation is in BCNF if for every non-trivial functional dependency $X \rightarrow Y$, the determinant $X$ is a superkey.

In every relation of our decomposed schema:
- In `CUSTOMER`: Determinants are `{customer_id}` and `{phone}` — both are unique candidate keys.
- In `RESTAURANT_TABLE`: Determinants are `{table_id}` and `{table_number}` — both are unique candidate keys.
- In `TIME_SLOT`: Determinants are `{slot_id}` and `{slot_label}` — both are unique candidate keys.
- In `RESERVATION`: Determinants are `{reservation_id}` and the active compound candidate key `{table_id, reservation_date, slot_id}` (enforced by the unique function-based index).

Because every determinant in every table is a superkey, the database is in **BCNF**.

---

## 4. Operational Anomalies Eliminated

### 1. Update Anomaly
- **Unnormalized Design:** If customer "Rahul Kumar" changes his phone number, every past and future reservation record referencing Rahul must be found and updated across thousands of rows. If even one record is missed, the database becomes inconsistent.
- **Normalized Design:** Rahul's phone number is stored in exactly **one row** in the `CUSTOMER` table. Updating that row updates his contact details across the entire system instantly with zero inconsistency risk.

### 2. Insertion Anomaly
- **Unnormalized Design:** A restaurant manager cannot add a new dining table (e.g., Table T12) or register a new customer until an actual reservation is made, because `reservation_id` would be NULL in the flat table.
- **Normalized Design:** Dining tables can be configured and customers can be registered independently at any time before any booking is created.

### 3. Deletion Anomaly
- **Unnormalized Design:** If customer "Priya Sharma" cancels her only reservation, deleting the reservation record inadvertently erases her customer profile and phone number from the restaurant's records.
- **Normalized Design:** Cancelling or deleting a reservation record does not delete the customer or the table; entity master data remains permanently intact.

---

## 5. Deliberate Architectural Decisions

1. **Operational vs. Temporal Status:**  
   `restaurant_table.status` stores permanent physical state (`AVAILABLE`, `MAINTENANCE`, `INACTIVE`). It is **never** flipped to "RESERVED" when a booking is made. Whether a table is free at a specific time is derived dynamically by checking if an active `RESERVATION` exists for `(table_id, reservation_date, slot_id)`.
2. **Double-Booking Prevention at Engine Level:**  
   Rather than relying only on Java application checks, Oracle enforces concurrency safety via the function-based unique index:
   ```sql
   CREATE UNIQUE INDEX uq_active_booking ON reservation (
       CASE WHEN status IN ('CONFIRMED', 'COMPLETED') THEN table_id         END,
       CASE WHEN status IN ('CONFIRMED', 'COMPLETED') THEN reservation_date END,
       CASE WHEN status IN ('CONFIRMED', 'COMPLETED') THEN slot_id          END
   );
   ```
   Because Oracle indices do not index all-null entries, `CANCELLED` bookings produce `NULL` for all three expressions and free the slot immediately for rebooking, while preserving full audit history.
