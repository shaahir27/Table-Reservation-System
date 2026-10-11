package service;

import dao.AuditDAO;
import dao.ReservationDAO;
import dao.TableDAO;
import exception.ReferenceInUseException;
import exception.ValidationException;
import model.RestaurantTable;
import security.AuthContext;
import security.Permission;
import security.Session;
import util.ValidationUtil;

import java.util.List;

/**
 * Service managing restaurant tables, capacity enforcement, and operational status transitions.
 */
public class TableService {
    private final TableDAO tableDAO;
    private final ReservationDAO reservationDAO;
    private final AuditDAO auditDAO;
    private final AuthContext auth;

    public TableService() {
        this(new TableDAO(), new ReservationDAO(), new AuditDAO(), Session.getInstance());
    }

    public TableService(TableDAO tableDAO) {
        this(tableDAO, new ReservationDAO(), new AuditDAO(), Session.getInstance());
    }

    public TableService(TableDAO tableDAO, AuditDAO auditDAO, AuthContext auth) {
        this(tableDAO, new ReservationDAO(), auditDAO, auth);
    }

    public TableService(TableDAO tableDAO, ReservationDAO reservationDAO, AuditDAO auditDAO, AuthContext auth) {
        this.tableDAO = tableDAO;
        this.reservationDAO = reservationDAO != null ? reservationDAO : new ReservationDAO();
        this.auditDAO = auditDAO != null ? auditDAO : new AuditDAO();
        this.auth = auth != null ? auth : Session.getInstance();
    }

    public RestaurantTable createTable(RestaurantTable table) {
        auth.require(Permission.TABLE_MANAGE);
        ValidationUtil.validateTable(table);
        table.setTableNumber(table.getTableNumber().trim().toUpperCase());

        if (tableDAO.existsByTableNumber(table.getTableNumber(), 0)) {
            throw new ValidationException("Table number already exists.");
        }

        tableDAO.add(table);
        auditDAO.log("TABLE_CREATED", "TABLE", (long) table.getTableId(),
                "Created table " + table.getTableNumber() + " (Cap: " + table.getCapacity() + ", Loc: " + table.getLocation() + ")");
        return table;
    }

    public void updateTable(RestaurantTable table) {
        auth.require(Permission.TABLE_MANAGE);
        ValidationUtil.validateTable(table);
        table.setTableNumber(table.getTableNumber().trim().toUpperCase());

        if (tableDAO.existsByTableNumber(table.getTableNumber(), table.getTableId())) {
            throw new ValidationException("Table number already exists.");
        }

        tableDAO.update(table);
        auditDAO.log("TABLE_UPDATED", "TABLE", (long) table.getTableId(),
                "Updated table " + table.getTableNumber() + " (Cap: " + table.getCapacity() + ", Status: " + table.getStatus() + ")");
    }

    public void deleteTable(int tableId) {
        auth.require(Permission.TABLE_MANAGE);
        try {
            tableDAO.delete(tableId);
            auditDAO.log("TABLE_DELETED", "TABLE", (long) tableId, "Deleted table id " + tableId);
        } catch (ReferenceInUseException e) {
            throw new ReferenceInUseException(
                    "Table has existing reservation history and cannot be deleted. Please set its status to INACTIVE instead to preserve audit records.",
                    e
            );
        }
    }

    public RestaurantTable getTableById(int tableId) {
        return tableDAO.getTableById(tableId);
    }

    public List<RestaurantTable> getAllTables() {
        return tableDAO.getAll();
    }

    public List<String> getDistinctLocations() {
        return tableDAO.getDistinctLocations();
    }

    /**
     * Returns the maximum seating capacity among active tables in the restaurant (fixes D4).
     */
    public int getMaxCapacity() {
        return tableDAO.getAll().stream()
                .filter(t -> t.getStatus() != model.TableStatus.INACTIVE)
                .mapToInt(RestaurantTable::getCapacity)
                .max()
                .orElse(10);
    }

    /**
     * Lists future active reservations on a table (for D22 maintenance / inactive protection).
     */
    public List<model.ReservationDetail> getFutureBlockingReservations(int tableId) {
        return reservationDAO.getFutureBlockingReservationsForTable(tableId, util.DbClock.today());
    }

    /**
     * Saves floor plan layout positions and shapes in a single transaction (P5 Floor Plan editor).
     */
    public void updateLayoutPositions(List<RestaurantTable> tables) {
        auth.require(Permission.TABLE_MANAGE);
        if (tables != null && !tables.isEmpty()) {
            tableDAO.updateLayoutPositions(tables);
            auditDAO.log("FLOOR_PLAN_UPDATED", "FLOOR_PLAN", 0L, "Updated positions for " + tables.size() + " tables");
        }
    }
}
