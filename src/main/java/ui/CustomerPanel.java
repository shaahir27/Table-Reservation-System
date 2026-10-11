package ui;

import com.formdev.flatlaf.FlatClientProperties;
import exception.ReferenceInUseException;
import exception.ValidationException;
import model.Customer;
import service.CustomerService;
import ui.components.ChipRow;
import ui.components.AppButton;
import ui.components.Card;
import ui.components.CustomerDetailDialog;
import ui.components.Form;
import ui.components.StyledTable;
import ui.components.Toast;
import util.AppLog;
import util.Ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import ui.components.Dialogs;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Customers management view (Section 10.5).
 * Left: master form with inline validation. Right: live debounced search and customers table.
 */
public class CustomerPanel extends BasePanel {
    private final CustomerService customerService;

    // Form fields
    private final JTextField nameField = new JTextField();
    private final JTextField phoneField = new JTextField();
    private final JTextField emailField = new JTextField();

    // Inline error labels
    private final JLabel nameErrorLabel = new JLabel(" ");
    private final JLabel phoneErrorLabel = new JLabel(" ");
    private final JLabel emailErrorLabel = new JLabel(" ");

    // Action buttons
    private JButton addBtn;
    private JButton updateBtn;
    private JButton deleteBtn;
    private JButton clearBtn;
    private JButton viewProfileBtn;

    // Table & Search
    private final JTextField searchField = new JTextField();
    private final DefaultTableModel tableModel;
    private final JTable customerTable;
    private final List<Customer> displayedCustomers = new ArrayList<>();
    private Customer selectedCustomer = null;
    private boolean suppressSelection;
    private Timer searchDebounceTimer;

    public CustomerPanel() {
        this(new CustomerService());
    }

    public CustomerPanel(CustomerService customerService) {
        super("Customer Management");
        this.customerService = customerService;

        tableModel = new DefaultTableModel(new String[]{"ID", "Customer Name", "Phone", "Email"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        customerTable = new StyledTable(tableModel).emptyMessage("No customers found.");

        // Center ID column
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        customerTable.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);

        customerTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !suppressSelection) {
                int selectedRow = customerTable.getSelectedRow();
                if (selectedRow >= 0 && selectedRow < displayedCustomers.size()) {
                    selectCustomer(displayedCustomers.get(selectedRow));
                }
            }
        });

        customerTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = customerTable.getSelectedRow();
                    if (row >= 0 && row < displayedCustomers.size()) {
                        openCustomerProfile(displayedCustomers.get(row));
                    }
                }
            }
        });

        initLayout();
        setupSearchDebounce();
    }

    @Override
    public void onShown() {
        loadCustomers();
    }

    private void initLayout() {
        contentPanel.setLayout(new BorderLayout(Theme.SPACE_24, 0));

        // LEFT: Form Card
        JPanel leftCard = createCardPanel();
        leftCard.setPreferredSize(new Dimension(380, 0));
        leftCard.setLayout(new BoxLayout(leftCard, BoxLayout.Y_AXIS));

        JLabel formTitle = new JLabel("Customer Details");
        formTitle.setFont(Theme.FONT_SECTION);
        formTitle.setForeground(Theme.TEXT_PRIMARY);
        formTitle.setAlignmentX(0.0f);
        leftCard.add(formTitle);
        leftCard.add(Box.createVerticalStrut(Theme.SPACE_16));

        // Name
        leftCard.add(createFieldLabel("Full Name *"));
        leftCard.add(Box.createVerticalStrut(6));
        Form.input(nameField);
        nameField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "e.g. Arun Sharma");
        leftCard.add(nameField);
        styleErrorLabel(nameErrorLabel);
        leftCard.add(nameErrorLabel);
        leftCard.add(Box.createVerticalStrut(Theme.SPACE_8));

        // Phone
        leftCard.add(createFieldLabel("Phone Number * (10 Digits)"));
        leftCard.add(Box.createVerticalStrut(6));
        Form.input(phoneField);
        phoneField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "e.g. 9876543210");
        leftCard.add(phoneField);
        styleErrorLabel(phoneErrorLabel);
        leftCard.add(phoneErrorLabel);
        leftCard.add(Box.createVerticalStrut(Theme.SPACE_8));

        // Email
        leftCard.add(createFieldLabel("Email Address (Optional)"));
        leftCard.add(Box.createVerticalStrut(6));
        Form.input(emailField);
        emailField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "e.g. arun@example.com");
        leftCard.add(emailField);
        styleErrorLabel(emailErrorLabel);
        leftCard.add(emailErrorLabel);
        leftCard.add(Box.createVerticalStrut(Theme.SPACE_16));

        // Form Buttons
        JPanel buttonRow1 = new ChipRow(8);
        buttonRow1.setOpaque(false);
        buttonRow1.setAlignmentX(0.0f);

        addBtn = createPrimaryButton("Add Customer");
        addBtn.addActionListener(e -> onAddCustomer());
        buttonRow1.add(addBtn);

        updateBtn = createSecondaryButton("Update");
        updateBtn.setEnabled(false);
        updateBtn.addActionListener(e -> onUpdateCustomer());
        buttonRow1.add(updateBtn);

        JPanel buttonRow2 = new ChipRow(8);
        buttonRow2.setOpaque(false);
        buttonRow2.setAlignmentX(0.0f);

        deleteBtn = createDangerButton("Delete");
        deleteBtn.setEnabled(false);
        deleteBtn.addActionListener(e -> onDeleteCustomer());
        buttonRow2.add(deleteBtn);

        clearBtn = createSecondaryButton("Clear");
        clearBtn.addActionListener(e -> clearForm());
        buttonRow2.add(clearBtn);

        for (JPanel r : new JPanel[]{buttonRow1, buttonRow2}) {
            for (java.awt.Component c : r.getComponents()) {
                if (c instanceof AppButton b) b.fixedWidth(162);
            }
        }
        leftCard.add(buttonRow1);
        leftCard.add(Box.createVerticalStrut(Theme.SPACE_8));
        leftCard.add(buttonRow2);
        leftCard.add(Box.createVerticalStrut(Theme.SPACE_12));

        JPanel buttonRow3 = new ChipRow(8);
        buttonRow3.setOpaque(false);
        buttonRow3.setAlignmentX(0.0f);
        viewProfileBtn = createSecondaryButton("👤 View 360° Profile");
        viewProfileBtn.setEnabled(false);
        viewProfileBtn.addActionListener(e -> {
            if (selectedCustomer != null) {
                openCustomerProfile(selectedCustomer);
            }
        });
        if (viewProfileBtn instanceof AppButton b) b.fixedWidth(332);
        buttonRow3.add(viewProfileBtn);
        leftCard.add(buttonRow3);

        leftCard.add(Box.createVerticalGlue());

        contentPanel.add(leftCard, BorderLayout.WEST);

        // RIGHT: Search + Table Card
        JPanel rightCard = createCardPanel();
        rightCard.setLayout(new BorderLayout(0, Theme.SPACE_16));

        // Search Bar
        JPanel searchBar = new JPanel(new BorderLayout(Theme.SPACE_12, 0));
        searchBar.setOpaque(false);
        searchField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Search customers by name or phone...");
        Form.input(searchField);
        searchBar.add(searchField, BorderLayout.CENTER);

        JButton refreshBtn = createSecondaryButton("Refresh");
        refreshBtn.addActionListener(e -> loadCustomers());
        searchBar.add(refreshBtn, BorderLayout.EAST);

        rightCard.add(searchBar, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(customerTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(Theme.NEUTRAL_TINT, 1));
        scrollPane.getViewport().setBackground(Theme.CARD_BG);
        rightCard.add(scrollPane, BorderLayout.CENTER);

        contentPanel.add(rightCard, BorderLayout.CENTER);
    }

    private void setupSearchDebounce() {
        searchDebounceTimer = new Timer(300, e -> {
            String query = searchField.getText().trim();
            filterCustomers(query);
        });
        searchDebounceTimer.setRepeats(false);

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                searchDebounceTimer.restart();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                searchDebounceTimer.restart();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                searchDebounceTimer.restart();
            }
        });
    }

    public void loadCustomers() {
        Ui.async(
                customerService::getAllCustomers,
                this::populateTable,
                error -> Toast.error(SwingUtilities.getWindowAncestor(this), "Failed to load customers: " + error.getMessage())
        );
    }

    private void filterCustomers(String query) {
        Ui.async(
                () -> customerService.searchCustomers(query),
                this::populateTable,
                error -> AppLog.error("Customer search failed: " + error.getMessage(), error)
        );
    }

    private void populateTable(List<Customer> customers) {
        displayedCustomers.clear();
        displayedCustomers.addAll(customers);
        suppressSelection = true;
        try {
            tableModel.setRowCount(0);
            for (Customer c : customers) {
                tableModel.addRow(new Object[]{
                        c.getCustomerId(),
                        c.getName(),
                        c.getPhone(),
                        c.getEmail() != null ? c.getEmail() : ""
                });
            }
            // keep the highlighted row in step with the form: still listed -> stay selected, else drop the stale selection
            if (selectedCustomer != null) {
                int keep = -1;
                for (int i = 0; i < displayedCustomers.size(); i++) {
                    if (displayedCustomers.get(i).getCustomerId() == selectedCustomer.getCustomerId()) keep = i;
                }
                if (keep >= 0) customerTable.setRowSelectionInterval(keep, keep);
            }
        } finally {
            suppressSelection = false;
        }
        if (customerTable instanceof StyledTable st) st.reveal();
    }

    private void selectCustomer(Customer customer) {
        this.selectedCustomer = customer;
        nameField.setText(customer.getName());
        phoneField.setText(customer.getPhone());
        emailField.setText(customer.getEmail() != null ? customer.getEmail() : "");

        clearValidationErrors();
        addBtn.setEnabled(false);
        updateBtn.setEnabled(true);
        deleteBtn.setEnabled(true);
        if (viewProfileBtn != null) viewProfileBtn.setEnabled(true);
    }

    private void clearForm() {
        selectedCustomer = null;
        customerTable.clearSelection();
        nameField.setText("");
        phoneField.setText("");
        emailField.setText("");

        clearValidationErrors();
        addBtn.setEnabled(true);
        updateBtn.setEnabled(false);
        deleteBtn.setEnabled(false);
        if (viewProfileBtn != null) viewProfileBtn.setEnabled(false);
    }

    private void onAddCustomer() {
        clearValidationErrors();
        Customer customer = new Customer(
                nameField.getText().trim(),
                phoneField.getText().trim(),
                emailField.getText().trim()
        );

        addBtn.setEnabled(false);
        addBtn.setText("Adding...");

        Ui.async(
                () -> customerService.createCustomer(customer),
                created -> {
                    addBtn.setEnabled(true);
                    addBtn.setText("Add Customer");
                    Toast.success(SwingUtilities.getWindowAncestor(this), "Customer added successfully");
                    clearForm();
                    loadCustomers();
                },
                error -> {
                    addBtn.setEnabled(true);
                    addBtn.setText("Add Customer");
                    handleFormError(error);
                }
        );
    }

    private void onUpdateCustomer() {
        if (selectedCustomer == null) return;
        clearValidationErrors();

        selectedCustomer.setName(nameField.getText().trim());
        selectedCustomer.setPhone(phoneField.getText().trim());
        selectedCustomer.setEmail(emailField.getText().trim());

        updateBtn.setEnabled(false);
        updateBtn.setText("Updating...");

        Ui.async(
                () -> {
                    customerService.updateCustomer(selectedCustomer);
                    return selectedCustomer;
                },
                updated -> {
                    updateBtn.setEnabled(true);
                    updateBtn.setText("Update");
                    Toast.success(SwingUtilities.getWindowAncestor(this), "Customer updated successfully");
                    clearForm();
                    loadCustomers();
                },
                error -> {
                    updateBtn.setEnabled(true);
                    updateBtn.setText("Update");
                    handleFormError(error);
                }
        );
    }

    private void openCustomerProfile(Customer c) {
        if (c == null) return;
        CustomerDetailDialog.show(
                SwingUtilities.getWindowAncestor(this),
                c,
                customerService,
                new service.ReservationService(),
                updated -> {
                    loadCustomers();
                    selectCustomer(updated);
                },
                bookedCust -> {
                    MainFrame mf = (MainFrame) SwingUtilities.getAncestorOfClass(MainFrame.class, this);
                    if (mf != null) {
                        mf.showScreen("NEW_RESERVATION");
                        Toast.info(mf, "Booking reservation for guest: " + bookedCust.getName());
                    }
                }
        );
    }

    private void onDeleteCustomer() {
        if (selectedCustomer == null) return;

        int confirm = Dialogs.showConfirmDialog(
                this,
                "Are you sure you want to delete customer \"" + selectedCustomer.getName() + "\" (" + selectedCustomer.getPhone() + ")?\nThis action cannot be undone.",
                "Confirm Customer Deletion",
                Dialogs.YES_NO_OPTION,
                Dialogs.WARNING_MESSAGE
        );

        if (confirm != Dialogs.YES_OPTION) return;

        int customerId = selectedCustomer.getCustomerId();
        deleteBtn.setEnabled(false);
        deleteBtn.setText("Deleting...");

        Ui.async(
                () -> {
                    customerService.deleteCustomer(customerId);
                    return true;
                },
                success -> {
                    deleteBtn.setEnabled(true);
                    deleteBtn.setText("Delete");
                    Toast.success(SwingUtilities.getWindowAncestor(this), "Customer deleted successfully");
                    clearForm();
                    loadCustomers();
                },
                error -> {
                    deleteBtn.setEnabled(true);
                    deleteBtn.setText("Delete");
                    if (error instanceof ReferenceInUseException) {
                        Dialogs.showMessageDialog(
                                this,
                                error.getMessage(),
                                "Cannot Delete Customer",
                                Dialogs.ERROR_MESSAGE
                        );
                    } else {
                        Toast.error(SwingUtilities.getWindowAncestor(this), error.getMessage());
                    }
                }
        );
    }

    private void handleFormError(Throwable error) {
        if (error instanceof ValidationException) {
            ValidationException ve = (ValidationException) error;
            Map<String, String> errors = ve.getFieldErrors();
            if (errors != null && !errors.isEmpty()) {
                if (errors.containsKey("name")) {
                    nameErrorLabel.setText(errors.get("name"));
                    nameField.putClientProperty(FlatClientProperties.OUTLINE, "error");
                }
                if (errors.containsKey("phone")) {
                    phoneErrorLabel.setText(errors.get("phone"));
                    phoneField.putClientProperty(FlatClientProperties.OUTLINE, "error");
                }
                if (errors.containsKey("email")) {
                    emailErrorLabel.setText(errors.get("email"));
                    emailField.putClientProperty(FlatClientProperties.OUTLINE, "error");
                }
            } else {
                Toast.error(SwingUtilities.getWindowAncestor(this), error.getMessage());
            }
        } else {
            Toast.error(SwingUtilities.getWindowAncestor(this), error.getMessage());
        }
    }

    private void clearValidationErrors() {
        nameErrorLabel.setText(" ");
        phoneErrorLabel.setText(" ");
        emailErrorLabel.setText(" ");

        nameField.putClientProperty(FlatClientProperties.OUTLINE, null);
        phoneField.putClientProperty(FlatClientProperties.OUTLINE, null);
        emailField.putClientProperty(FlatClientProperties.OUTLINE, null);
    }

    private JPanel createCardPanel() {
        return new Card(Theme.SPACE_24);
    }

    private JLabel createFieldLabel(String text) {
        return Form.label(text);
    }

    private void styleErrorLabel(JLabel label) {
        label.setFont(Theme.FONT_SMALL);
        label.setForeground(Theme.DANGER);
        label.setAlignmentX(0.0f);
        label.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
    }

    private JButton createPrimaryButton(String text) {
        return AppButton.primary(text);
    }

    private JButton createSecondaryButton(String text) {
        return AppButton.secondary(text);
    }

    private JButton createDangerButton(String text) {
        return AppButton.danger(text);
    }
}
