package service;

import dao.AuditDAO;
import dao.CustomerDAO;
import exception.ValidationException;
import model.Customer;
import security.AuthContext;
import security.Permission;
import security.Session;
import util.ValidationUtil;

import java.util.List;

/**
 * Service managing customer business logic, normalization, and validation rules.
 */
public class CustomerService {
    private final CustomerDAO customerDAO;
    private final AuditDAO auditDAO;
    private final AuthContext auth;

    public CustomerService() {
        this(new CustomerDAO(), new AuditDAO(), Session.getInstance());
    }

    public CustomerService(CustomerDAO customerDAO) {
        this(customerDAO, new AuditDAO(), Session.getInstance());
    }

    public CustomerService(CustomerDAO customerDAO, AuditDAO auditDAO, AuthContext auth) {
        this.customerDAO = customerDAO;
        this.auditDAO = auditDAO != null ? auditDAO : new AuditDAO();
        this.auth = auth != null ? auth : Session.getInstance();
    }

    public Customer createCustomer(Customer customer) {
        auth.require(Permission.CUSTOMER_EDIT);
        ValidationUtil.validateCustomer(customer);
        customer.setPhone(ValidationUtil.normalizePhone(customer.getPhone()));

        if (customerDAO.existsByPhone(customer.getPhone(), 0)) {
            throw new ValidationException("Phone number is already registered to another customer.");
        }

        customerDAO.add(customer);
        auditDAO.log("CUSTOMER_CREATED", "CUSTOMER", (long) customer.getCustomerId(),
                "Created customer: " + customer.getName() + " (" + customer.getPhone() + ")");
        return customer;
    }

    public void updateCustomer(Customer customer) {
        auth.require(Permission.CUSTOMER_EDIT);
        ValidationUtil.validateCustomer(customer);
        customer.setPhone(ValidationUtil.normalizePhone(customer.getPhone()));

        if (customerDAO.existsByPhone(customer.getPhone(), customer.getCustomerId())) {
            throw new ValidationException("Phone number is already registered to another customer.");
        }

        customerDAO.update(customer);
        auditDAO.log("CUSTOMER_UPDATED", "CUSTOMER", (long) customer.getCustomerId(),
                "Updated customer: " + customer.getName() + " (" + customer.getPhone() + ")");
    }

    public void deleteCustomer(int customerId) {
        auth.require(Permission.CUSTOMER_DELETE);
        customerDAO.delete(customerId);
        auditDAO.log("CUSTOMER_DELETED", "CUSTOMER", (long) customerId, "Deleted customer id " + customerId);
    }

    public Customer getCustomerById(int customerId) {
        return customerDAO.getCustomerById(customerId);
    }

    public List<Customer> getAllCustomers() {
        return customerDAO.getAll();
    }

    public List<Customer> searchCustomers(String query) {
        return customerDAO.searchCustomers(query);
    }
}
