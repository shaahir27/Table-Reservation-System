package service;

import dao.CustomerDAO;
import exception.ValidationException;
import model.Customer;
import util.ValidationUtil;

import java.util.List;

/**
 * Service managing customer business logic, normalization, and validation rules.
 */
public class CustomerService {
    private final CustomerDAO customerDAO;

    public CustomerService() {
        this(new CustomerDAO());
    }

    public CustomerService(CustomerDAO customerDAO) {
        this.customerDAO = customerDAO;
    }

    public Customer createCustomer(Customer customer) {
        ValidationUtil.validateCustomer(customer);
        customer.setPhone(ValidationUtil.normalizePhone(customer.getPhone()));

        if (customerDAO.existsByPhone(customer.getPhone(), 0)) {
            throw new ValidationException("Phone number is already registered to another customer.");
        }

        customerDAO.add(customer);
        return customer;
    }

    public void updateCustomer(Customer customer) {
        ValidationUtil.validateCustomer(customer);
        customer.setPhone(ValidationUtil.normalizePhone(customer.getPhone()));

        if (customerDAO.existsByPhone(customer.getPhone(), customer.getCustomerId())) {
            throw new ValidationException("Phone number is already registered to another customer.");
        }

        customerDAO.update(customer);
    }

    public void deleteCustomer(int customerId) {
        customerDAO.delete(customerId);
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
