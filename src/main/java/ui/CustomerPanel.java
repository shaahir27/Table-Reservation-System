package ui;

import com.formdev.flatlaf.FlatClientProperties;
import exception.AppException;
import exception.ReferenceInUseException;
import exception.ValidationException;
import model.Customer;
import service.CustomerService;
import ui.components.Toast;
import util.AppLog;
import util.Ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
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

    // Table & Search
    private final JTextField searchField = new JTextField();
    private final DefaultTableModel tableModel;
    private final JTable customerTable;
    private final List<Customer> displayedCustomers = new ArrayList<>();
    private Customer selectedCustomer = null;
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

        customerTable = new JTable(tableModel);
        customerTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        customerTable.setRowHeight(38);
        customerTable.getTableHeader().setPreferredSize(new Dimension(0, 40));
        customerTable.getTableHeader().setFont(Theme.FONT_BODY_BOLD);
        customerTable.setFont(Theme.FONT_BODY);

        // Center ID column
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        customerTable.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);
        customerTable.getColumnModel().getColumn(0).setMaxWidth(70);

        customerTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = customerTable.getSelectedRow();
                if (selectedRow >= 0 && selectedRow < displayedCustomers.size()) {
                    selectCustomer(displayedCustomers.get(selectedRow));
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
        leftCard.setPreferredSize(new Dimension(360, 0));
        leftCard.setLayout(new BoxLayout(leftCard, BoxLayout.Y_AXIS));

        JLabel formTitle = new JLabel("Customer Details");
        formTitle.setFont(Theme.FONT_SECTION);
        formTitle.setForeground(Theme.TEXT_PRIMARY);
        leftCard.add(formTitle);
        leftCard.add(Box.createVerticalStrut(Theme.SPACE_16));

        // Name
        leftCard.add(createFieldLabel("Full Name *"));
        nameField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "e.g. Arun Sharma");
        leftCard.add(nameField);
        styleErrorLabel(nameErrorLabel);
        leftCard.add(nameErrorLabel);
        leftCard.add(Box.createVerticalStrut(Theme.SPACE_8));

        // Phone
        leftCard.add(createFieldLabel("Phone Number * (10 Digits)"));
        phoneField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "e.g. 9876543210");
        leftCard.add(phoneField);
        styleErrorLabel(phoneErrorLabel);
        leftCard.add(phoneErrorLabel);
        leftCard.add(Box.createVerticalStrut(Theme.SPACE_8));

        // Email
        leftCard.add(createFieldLabel("Email Address (Optional)"));
        emailField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "e.g. arun@example.com");
        leftCard.add(emailField);
        styleErrorLabel(emailErrorLabel);
        leftCard.add(emailErrorLabel);
        leftCard.add(Box.createVerticalStrut(Theme.SPACE_16));

        // Form Buttons
        JPanel buttonRow1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttonRow1.setOpaque(false);

        addBtn = createPrimaryButton("Add Customer");
        addBtn.addActionListener(e -> onAddCustomer());
        buttonRow1.add(addBtn);

        updateBtn = createSecondaryButton("Update");
        updateBtn.setEnabled(false);
        updateBtn.addActionListener(e -> onUpdateCustomer());
        buttonRow1.add(updateBtn);

        JPanel buttonRow2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttonRow2.setOpaque(false);

        deleteBtn = createDangerButton("Delete");
        deleteBtn.setEnabled(false);
        deleteBtn.addActionListener(e -> onDeleteCustomer());
        buttonRow2.add(deleteBtn);

        clearBtn = createSecondaryButton("Clear");
        clearBtn.addActionListener(e -> clearForm());
        buttonRow2.add(clearBtn);

        leftCard.add(buttonRow1);
        leftCard.add(Box.createVerticalStrut(Theme.SPACE_8));
        leftCard.add(buttonRow2);
        leftCard.add(Box.createVerticalGlue());

        contentPanel.add(leftCard, BorderLayout.WEST);

        // RIGHT: Search + Table Card
        JPanel rightCard = createCardPanel();
        rightCard.setLayout(new BorderLayout(0, Theme.SPACE_16));

        // Search Bar
        JPanel searchBar = new JPanel(new BorderLayout(Theme.SPACE_12, 0));
        searchBar.setOpaque(false);
        searchField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Search customers by name or phone...");
        searchField.setPreferredSize(new Dimension(300, 38));
        searchBar.add(searchField, BorderLayout.CENTER);

        JButton refreshBtn = createSecondaryButton("Refresh");
        refreshBtn.addActionListener(e -> loadCustomers());
        searchBar.add(refreshBtn, BorderLayout.EAST);

        rightCard.add(searchBar, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(customerTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(Theme.CARD_BORDER, 1));
        scrollPane.getViewport().setBackground(Color.WHITE);
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
        tableModel.setRowCount(0);

        for (Customer c : customers) {
            tableModel.addRow(new Object[]{
                    c.getCustomerId(),
                    c.getName(),
                    c.getPhone(),
                    c.getEmail() != null ? c.getEmail() : ""
            });
        }
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

    private void onDeleteCustomer() {
        if (selectedCustomer == null) return;

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to delete customer \"" + selectedCustomer.getName() + "\" (" + selectedCustomer.getPhone() + ")?\nThis action cannot be undone.",
                "Confirm Customer Deletion",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (confirm != JOptionPane.YES_OPTION) return;

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
                        JOptionPane.showMessageDialog(
                                this,
                                error.getMessage(),
                                "Cannot Delete Customer",
                                JOptionPane.ERROR_MESSAGE
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
        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.CARD_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                g2.setColor(Theme.CARD_BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_24, Theme.SPACE_24, Theme.SPACE_24, Theme.SPACE_24));
        return card;
    }

    private JLabel createFieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.FONT_SMALL_BOLD);
        label.setForeground(Theme.TEXT_MUTED);
        return label;
    }

    private void styleErrorLabel(JLabel label) {
        label.setFont(Theme.FONT_SMALL);
        label.setForeground(Theme.DANGER);
        label.setAlignmentX(0.0f);
    }

    private JButton createPrimaryButton(String text) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isEnabled() ? (getModel().isRollover() ? Theme.ACCENT_GOLD_HOVER : Theme.ACCENT_GOLD) : Theme.CARD_BORDER);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(Theme.FONT_BODY_BOLD);
        btn.setForeground(Color.WHITE);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(140, 38));
        return btn;
    }

    private JButton createSecondaryButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(Theme.FONT_BODY);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(100, 38));
        return btn;
    }

    private JButton createDangerButton(String text) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                if (isEnabled()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(192, 57, 43, 20));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                    g2.setColor(Theme.DANGER);
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                    g2.dispose();
                }
                super.paintComponent(g);
            }
        };
        btn.setFont(Theme.FONT_BODY_BOLD);
        btn.setForeground(Theme.DANGER);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(100, 38));
        return btn;
    }
}
