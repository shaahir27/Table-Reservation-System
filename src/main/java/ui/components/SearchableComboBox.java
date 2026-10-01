package ui.components;

import com.formdev.flatlaf.FlatClientProperties;
import model.Customer;
import ui.Theme;

import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Filterable customer selector pairing a live search field with an auto-updating JComboBox.
 */
public class SearchableComboBox extends JPanel {
    private final JTextField searchFilterField = new JTextField();
    private final JComboBox<Customer> customerComboBox = new JComboBox<>();
    private final List<Customer> masterCustomerList = new ArrayList<>();
    private Consumer<Customer> onSelectionChanged;

    public SearchableComboBox() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);

        searchFilterField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Type name or phone to filter...");
        searchFilterField.setPreferredSize(new Dimension(320, 36));
        searchFilterField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        customerComboBox.setPreferredSize(new Dimension(320, 38));
        customerComboBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        customerComboBox.setFont(Theme.FONT_BODY);

        add(searchFilterField);
        add(javax.swing.Box.createVerticalStrut(Theme.SPACE_8));
        add(customerComboBox);

        searchFilterField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                filterItems();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                filterItems();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                filterItems();
            }
        });

        customerComboBox.addActionListener(e -> {
            if (onSelectionChanged != null) {
                onSelectionChanged.accept(getSelectedCustomer());
            }
        });
    }

    public void setOnSelectionChanged(Consumer<Customer> onSelectionChanged) {
        this.onSelectionChanged = onSelectionChanged;
    }

    public void setCustomers(List<Customer> customers) {
        masterCustomerList.clear();
        if (customers != null) {
            masterCustomerList.addAll(customers);
        }
        filterItems();
    }

    private void filterItems() {
        String filter = searchFilterField.getText().trim().toLowerCase();
        Customer currentSelection = (Customer) customerComboBox.getSelectedItem();

        DefaultComboBoxModel<Customer> model = new DefaultComboBoxModel<>();
        for (Customer c : masterCustomerList) {
            if (filter.isEmpty() || 
                (c.getName() != null && c.getName().toLowerCase().contains(filter)) ||
                (c.getPhone() != null && c.getPhone().contains(filter))) {
                model.addElement(c);
            }
        }

        customerComboBox.setModel(model);

        if (currentSelection != null) {
            for (int i = 0; i < model.getSize(); i++) {
                if (model.getElementAt(i).getCustomerId() == currentSelection.getCustomerId()) {
                    customerComboBox.setSelectedIndex(i);
                    break;
                }
            }
        }
    }

    public Customer getSelectedCustomer() {
        return (Customer) customerComboBox.getSelectedItem();
    }

    public void setSelectedCustomer(Customer customer) {
        if (customer == null) {
            customerComboBox.setSelectedItem(null);
            return;
        }

        // Ensure customer is in master list
        boolean exists = masterCustomerList.stream().anyMatch(c -> c.getCustomerId() == customer.getCustomerId());
        if (!exists) {
            masterCustomerList.add(0, customer);
            filterItems();
        }

        for (int i = 0; i < customerComboBox.getItemCount(); i++) {
            Customer item = customerComboBox.getItemAt(i);
            if (item.getCustomerId() == customer.getCustomerId()) {
                customerComboBox.setSelectedIndex(i);
                break;
            }
        }
    }

    public void clearFilter() {
        searchFilterField.setText("");
    }
}
