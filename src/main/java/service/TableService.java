package service;

import dao.TableDAO;
import exception.ReferenceInUseException;
import exception.ValidationException;
import model.RestaurantTable;
import util.ValidationUtil;

import java.util.List;

/**
 * Service managing restaurant tables, capacity enforcement, and operational status transitions.
 */
public class TableService {
    private final TableDAO tableDAO;

    public TableService() {
        this(new TableDAO());
    }

    public TableService(TableDAO tableDAO) {
        this.tableDAO = tableDAO;
    }

    public RestaurantTable createTable(RestaurantTable table) {
        ValidationUtil.validateTable(table);
        table.setTableNumber(table.getTableNumber().trim().toUpperCase());

        if (tableDAO.existsByTableNumber(table.getTableNumber(), 0)) {
            throw new ValidationException("Table number already exists.");
        }

        tableDAO.add(table);
        return table;
    }

    public void updateTable(RestaurantTable table) {
        ValidationUtil.validateTable(table);
        table.setTableNumber(table.getTableNumber().trim().toUpperCase());

        if (tableDAO.existsByTableNumber(table.getTableNumber(), table.getTableId())) {
            throw new ValidationException("Table number already exists.");
        }

        tableDAO.update(table);
    }

    public void deleteTable(int tableId) {
        try {
            tableDAO.delete(tableId);
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
}
