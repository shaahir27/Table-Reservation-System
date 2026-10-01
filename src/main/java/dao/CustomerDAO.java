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

    @Override
    public void add(Customer customer) {
        addCustomer(customer);
    }

    public void addCustomer(Customer customer) {
        try (Connection con = DatabaseConnection.getConnection()) {
            addCustomer(con, customer);
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "CustomerDAO.addCustomer");
        }
    }

    public void addCustomer(Connection con, Customer customer) throws SQLException {
        String sql = "INSERT INTO customer (customer_id, customer_name, phone, email) " +
                     "VALUES (customer_seq.NEXTVAL, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, new String[]{"CUSTOMER_ID"})) {
            ps.setString(1, customer.getName());
            ps.setString(2, customer.getPhone());
            ps.setString(3, customer.getEmail());
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
        String sql = "SELECT customer_id, customer_name, phone, email FROM customer WHERE customer_id = ?";
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
        try (Connection con = DatabaseConnection.getConnection()) {
            return getAllCustomers(con);
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "CustomerDAO.getAllCustomers");
        }
    }

    public List<Customer> getAllCustomers(Connection con) throws SQLException {
        String sql = "SELECT customer_id, customer_name, phone, email FROM customer ORDER BY customer_name ASC";
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
        try (Connection con = DatabaseConnection.getConnection()) {
            return searchCustomers(con, query);
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "CustomerDAO.searchCustomers");
        }
    }

    public List<Customer> searchCustomers(Connection con, String query) throws SQLException {
        if (query == null || query.trim().isEmpty()) {
            return getAllCustomers(con);
        }

        String sql = "SELECT customer_id, customer_name, phone, email FROM customer " +
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
        try (Connection con = DatabaseConnection.getConnection()) {
            updateCustomer(con, customer);
        } catch (SQLException e) {
            throw OracleErrorMapper.map(e, "CustomerDAO.updateCustomer");
        }
    }

    public void updateCustomer(Connection con, Customer customer) throws SQLException {
        String sql = "UPDATE customer SET customer_name = ?, phone = ?, email = ? WHERE customer_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, customer.getName());
            ps.setString(2, customer.getPhone());
            ps.setString(3, customer.getEmail());
            ps.setInt(4, customer.getCustomerId());
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
        return new Customer(
                rs.getInt("customer_id"),
                rs.getString("customer_name"),
                rs.getString("phone"),
                rs.getString("email")
        );
    }
}
