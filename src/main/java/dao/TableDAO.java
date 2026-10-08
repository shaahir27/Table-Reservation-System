package dao;

import db.DatabaseConnection;
import model.RestaurantTable;
import model.TableAvailability;
import model.TableState;
import model.TableStatus;
import util.OracleErrorMapper;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for RestaurantTable entities. Implements generic CrudDAO.
 * Manages table catalog, lock acquisitions, and spatial/availability queries.
 */
public class TableDAO implements CrudDAO<RestaurantTable, Integer> {

    @Override
    public void add(RestaurantTable table) {
        addTable(table);
    }

    public void addTable(RestaurantTable table) {
        try (Connection con = DatabaseConnection.getConnection()) {
            addTable(con, table);
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "TableDAO.addTable");
        }
    }

    public void addTable(Connection con, RestaurantTable table) throws SQLException {
        String sql = "INSERT INTO restaurant_table (table_id, table_number, capacity, location, status) " +
                     "VALUES (table_seq.NEXTVAL, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, new String[]{"TABLE_ID"})) {
            ps.setString(1, table.getTableNumber());
            ps.setInt(2, table.getCapacity());
            ps.setString(3, table.getLocation());
            ps.setString(4, table.getStatus().name());
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs != null && rs.next()) {
                    table.setTableId(rs.getInt(1));
                }
            }
        }
    }

    @Override
    public Optional<RestaurantTable> getById(Integer id) {
        return Optional.ofNullable(getTableById(id));
    }

    public RestaurantTable getTableById(int tableId) {
        try (Connection con = DatabaseConnection.getConnection()) {
            return getTableById(con, tableId);
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "TableDAO.getTableById");
        }
    }

    public RestaurantTable getTableById(Connection con, int tableId) throws SQLException {
        String sql = "SELECT table_id, table_number, capacity, location, status FROM restaurant_table WHERE table_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, tableId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    @Override
    public List<RestaurantTable> getAll() {
        return getAllTables();
    }

    public List<RestaurantTable> getAllTables() {
        try (Connection con = DatabaseConnection.getConnection()) {
            return getAllTables(con);
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "TableDAO.getAllTables");
        }
    }

    public List<RestaurantTable> getAllTables(Connection con) throws SQLException {
        String sql = "SELECT table_id, table_number, capacity, location, status FROM restaurant_table ORDER BY table_number ASC";
        List<RestaurantTable> list = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    @Override
    public void update(RestaurantTable table) {
        updateTable(table);
    }

    public void updateTable(RestaurantTable table) {
        try (Connection con = DatabaseConnection.getConnection()) {
            updateTable(con, table);
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "TableDAO.updateTable");
        }
    }

    public void updateTable(Connection con, RestaurantTable table) throws SQLException {
        String sql = "UPDATE restaurant_table SET table_number = ?, capacity = ?, location = ?, status = ? WHERE table_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, table.getTableNumber());
            ps.setInt(2, table.getCapacity());
            ps.setString(3, table.getLocation());
            ps.setString(4, table.getStatus().name());
            ps.setInt(5, table.getTableId());
            ps.executeUpdate();
        }
    }

    @Override
    public void delete(Integer id) {
        deleteTable(id);
    }

    public void deleteTable(int tableId) {
        try (Connection con = DatabaseConnection.getConnection()) {
            deleteTable(con, tableId);
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "TableDAO.deleteTable");
        }
    }

    public void deleteTable(Connection con, int tableId) throws SQLException {
        String sql = "DELETE FROM restaurant_table WHERE table_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, tableId);
            ps.executeUpdate();
        }
    }

    public boolean existsByTableNumber(String tableNumber, int excludeTableId) {
        try (Connection con = DatabaseConnection.getConnection()) {
            String sql = "SELECT 1 FROM restaurant_table WHERE table_number = ? AND table_id <> ?";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, tableNumber);
                ps.setInt(2, excludeTableId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "TableDAO.existsByTableNumber");
        }
    }

    /**
     * Serializes concurrent bookings of the same table during reservation transactions.
     */
    public void lockTable(Connection con, int tableId) throws SQLException {
        String sql = "SELECT table_id FROM restaurant_table WHERE table_id = ? FOR UPDATE WAIT 5";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, tableId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Table with ID " + tableId + " does not exist.");
                }
            }
        }
    }

    /**
     * Queries available tables with status = AVAILABLE, capacity >= guests, and no active booking for the slot.
     */
    public List<RestaurantTable> getAvailableTables(LocalDate date, int slotId, int guests) {
        try (Connection con = DatabaseConnection.getConnection()) {
            return getAvailableTables(con, date, slotId, guests);
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "TableDAO.getAvailableTables");
        }
    }

    public List<RestaurantTable> getAvailableTables(Connection con, LocalDate date, int slotId, int guests) throws SQLException {
        String sql = "SELECT t.table_id, t.table_number, t.capacity, t.location, t.status " +
                     "FROM   restaurant_table t " +
                     "WHERE  t.status = 'AVAILABLE' " +
                     "AND    t.capacity >= ? " +
                     "AND    NOT EXISTS (SELECT 1 FROM reservation r " +
                     "                   WHERE  r.table_id = t.table_id " +
                     "                   AND    r.reservation_date = ? " +
                     "                   AND    r.slot_id = ? " +
                     "                   AND    r.status IN " + SqlFragments.ACTIVE_BOOKING_STATUSES_IN + ") " +
                     "ORDER  BY t.capacity ASC, t.table_number ASC";

        List<RestaurantTable> list = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, guests);
            ps.setDate(2, Date.valueOf(date));
            ps.setInt(3, slotId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    /**
     * Powers the visual floor view: returns TableAvailability objects reflecting RESERVED, AVAILABLE, or MAINTENANCE.
     */
    public List<TableAvailability> getTableStates(LocalDate date, int slotId) {
        try (Connection con = DatabaseConnection.getConnection()) {
            return getTableStates(con, date, slotId);
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "TableDAO.getTableStates");
        }
    }

    public List<TableAvailability> getTableStates(Connection con, LocalDate date, int slotId) throws SQLException {
        String sql = "SELECT t.table_id, t.table_number, t.capacity, t.location, t.status, " +
                     "       CASE WHEN r.reservation_id IS NULL THEN 0 ELSE 1 END AS is_reserved " +
                     "FROM   restaurant_table t " +
                     "LEFT   JOIN reservation r " +
                     "       ON  r.table_id = t.table_id AND r.reservation_date = ? AND r.slot_id = ? " +
                     "       AND r.status IN " + SqlFragments.ACTIVE_BOOKING_STATUSES_IN + " " +
                     "WHERE  t.status <> 'INACTIVE' " +
                     "ORDER  BY t.table_number ASC";

        List<TableAvailability> list = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(date));
            ps.setInt(2, slotId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    RestaurantTable t = mapRow(rs);
                    int isReserved = rs.getInt("is_reserved");

                    TableState state;
                    if (t.getStatus() == TableStatus.MAINTENANCE) {
                        state = TableState.MAINTENANCE;
                    } else if (isReserved == 1) {
                        state = TableState.RESERVED;
                    } else {
                        state = TableState.AVAILABLE;
                    }

                    list.add(new TableAvailability(t, state, false));
                }
            }
        }
        return list;
    }

    public List<String> getDistinctLocations() {
        try (Connection con = DatabaseConnection.getConnection()) {
            String sql = "SELECT DISTINCT location FROM restaurant_table WHERE status <> 'INACTIVE' ORDER BY location ASC";
            List<String> locations = new ArrayList<>();
            try (PreparedStatement ps = con.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    locations.add(rs.getString(1));
                }
            }
            return locations;
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "TableDAO.getDistinctLocations");
        }
    }

    private RestaurantTable mapRow(ResultSet rs) throws SQLException {
        return new RestaurantTable(
                rs.getInt("table_id"),
                rs.getString("table_number"),
                rs.getInt("capacity"),
                rs.getString("location"),
                TableStatus.valueOf(rs.getString("status"))
        );
    }
}
