package dao;

import db.DatabaseConnection;
import model.Customer;
import util.OracleErrorMapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for Customer entities. Implements generic CrudDAO.
 * Strictly uses PreparedStatements with parameter binding.
 */
public class CustomerDAO implements CrudDAO<Customer, Integer> {

    private static volatile boolean schemaInitialized = false;

    private static synchronized void ensureSchema() {
        if (schemaInitialized) return;
        try (Connection con = DatabaseConnection.getConnection();
             java.sql.Statement st = con.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT tags, notes FROM customer WHERE ROWNUM = 1")) {
                // columns already exist
            } catch (SQLException e) {
                try {
                    st.execute("ALTER TABLE customer ADD (tags VARCHAR2(255), notes VARCHAR2(500))");
                } catch (SQLException ignored) {
                }
            }
            try {
                st.execute("MERGE INTO customer c USING (" +
                        "  SELECT TO_NUMBER(SUBSTR(setting_key, 11)) AS cid, setting_value AS tags " +
                        "  FROM app_setting WHERE setting_key LIKE 'cust_tags_%'" +
                        ") s ON (c.customer_id = s.cid) " +
                        "WHEN MATCHED THEN UPDATE SET c.tags = s.tags");
                st.execute("MERGE INTO customer c USING (" +
                        "  SELECT TO_NUMBER(SUBSTR(setting_key, 12)) AS cid, setting_value AS notes " +
                        "  FROM app_setting WHERE setting_key LIKE 'cust_notes_%'" +
                        ") s ON (c.customer_id = s.cid) " +
                        "WHEN MATCHED THEN UPDATE SET c.notes = s.notes");
                st.execute("DELETE FROM app_setting WHERE setting_key LIKE 'cust_%'");
            } catch (Exception ignored) {
            }
            schemaInitialized = true;
        } catch (Exception e) {
            schemaInitialized = true;
        }
    }

    @Override
    public void add(Customer customer) {
        addCustomer(customer);
    }

    public void addCustomer(Customer customer) {
        ensureSchema();
        try (Connection con = DatabaseConnection.getConnection()) {
            addCustomer(con, customer);
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "CustomerDAO.addCustomer");
        }
    }

    public void addCustomer(Connection con, Customer customer) throws SQLException {
        ensureSchema();
        String sql = "INSERT INTO customer (customer_id, customer_name, phone, email, tags, notes) " +
                     "VALUES (customer_seq.NEXTVAL, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, new String[]{"CUSTOMER_ID"})) {
            ps.setString(1, customer.getName());
            ps.setString(2, customer.getPhone());
            ps.setString(3, customer.getEmail());
            ps.setString(4, customer.getTags() != null ? customer.getTags() : "");
            ps.setString(5, customer.getNotes() != null ? customer.getNotes() : "");
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs != null && rs.next()) {
                    customer.setCustomerId(rs.getInt(1));
                }
            }
        }
    }

    @Override
    public Optional<Customer> getById(Integer id) {
        return Optional.ofNullable(getCustomerById(id));
    }

    public Customer getCustomerById(int customerId) {
        try (Connection con = DatabaseConnection.getConnection()) {
            return getCustomerById(con, customerId);
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "CustomerDAO.getCustomerById");
        }
    }

    public Customer getCustomerById(Connection con, int customerId) throws SQLException {
        ensureSchema();
        String sql = "SELECT customer_id, customer_name, phone, email, tags, notes FROM customer WHERE customer_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    @Override
    public List<Customer> getAll() {
        return getAllCustomers();
    }

    public List<Customer> getAllCustomers() {
        ensureSchema();
        try (Connection con = DatabaseConnection.getConnection()) {
            return getAllCustomers(con);
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "CustomerDAO.getAllCustomers");
        }
    }

    public List<Customer> getAllCustomers(Connection con) throws SQLException {
        ensureSchema();
        String sql = "SELECT customer_id, customer_name, phone, email, tags, notes FROM customer ORDER BY customer_name ASC";
        List<Customer> list = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    public List<Customer> searchCustomers(String query) {
        ensureSchema();
        try (Connection con = DatabaseConnection.getConnection()) {
            return searchCustomers(con, query);
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "CustomerDAO.searchCustomers");
        }
    }

    public List<Customer> searchCustomers(Connection con, String query) throws SQLException {
        ensureSchema();
        if (query == null || query.trim().isEmpty()) {
            return getAllCustomers(con);
        }

        String sql = "SELECT customer_id, customer_name, phone, email, tags, notes FROM customer " +
                     "WHERE UPPER(customer_name) LIKE UPPER(?) OR phone LIKE ? " +
                     "ORDER BY customer_name ASC";
        List<Customer> list = new ArrayList<>();
        String wildcard = "%" + query.trim() + "%";

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, wildcard);
            ps.setString(2, wildcard);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    @Override
    public void update(Customer customer) {
        updateCustomer(customer);
    }

    public void updateCustomer(Customer customer) {
        ensureSchema();
        try (Connection con = DatabaseConnection.getConnection()) {
            updateCustomer(con, customer);
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "CustomerDAO.updateCustomer");
        }
    }

    public void updateCustomer(Connection con, Customer customer) throws SQLException {
        ensureSchema();
        String sql = "UPDATE customer SET customer_name = ?, phone = ?, email = ?, tags = ?, notes = ? WHERE customer_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, customer.getName());
            ps.setString(2, customer.getPhone());
            ps.setString(3, customer.getEmail());
            ps.setString(4, customer.getTags() != null ? customer.getTags() : "");
            ps.setString(5, customer.getNotes() != null ? customer.getNotes() : "");
            ps.setInt(6, customer.getCustomerId());
            ps.executeUpdate();
        }
    }

    @Override
    public void delete(Integer id) {
        deleteCustomer(id);
    }

    public void deleteCustomer(int customerId) {
        try (Connection con = DatabaseConnection.getConnection()) {
            deleteCustomer(con, customerId);
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "CustomerDAO.deleteCustomer");
        }
    }

    public void deleteCustomer(Connection con, int customerId) throws SQLException {
        String sql = "DELETE FROM customer WHERE customer_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            ps.executeUpdate();
        }
    }

    public boolean existsByPhone(String phone, int excludeCustomerId) {
        try (Connection con = DatabaseConnection.getConnection()) {
            String sql = "SELECT 1 FROM customer WHERE phone = ? AND customer_id <> ?";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, phone);
                ps.setInt(2, excludeCustomerId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "CustomerDAO.existsByPhone");
        }
    }

    private Customer mapRow(ResultSet rs) throws SQLException {
        String tags = "";
        String notes = "";
        try {
            tags = rs.getString("tags");
        } catch (SQLException ignored) {
        }
        try {
            notes = rs.getString("notes");
        } catch (SQLException ignored) {
        }
        return new Customer(
                rs.getInt("customer_id"),
                rs.getString("customer_name"),
                rs.getString("phone"),
                rs.getString("email"),
                tags != null ? tags : "",
                notes != null ? notes : ""
        );
    }
}
