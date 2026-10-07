package ui.components;

import com.formdev.flatlaf.FlatClientProperties;
import exception.ValidationException;
import model.Customer;
import service.CustomerService;
import ui.Theme;
import util.Ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Modal dialog for quickly registering a new customer during reservation creation.
 */
public class AddCustomerDialog extends JDialog {
    private final CustomerService customerService;
    private final Consumer<Customer> onCustomerSaved;

    private final JTextField nameField = new JTextField();
    private final JTextField phoneField = new JTextField();
    private final JTextField emailField = new JTextField();

    private final JLabel nameErrorLabel = new JLabel(" ");
    private final JLabel phoneErrorLabel = new JLabel(" ");
    private final JLabel emailErrorLabel = new JLabel(" ");

    private JButton saveBtn;
    private JButton cancelBtn;

    public AddCustomerDialog(Window owner, CustomerService customerService, Consumer<Customer> onCustomerSaved) {
        super(owner, "Register New Customer", ModalityType.APPLICATION_MODAL);
        this.customerService = customerService;
        this.onCustomerSaved = onCustomerSaved;

        setLayout(new BorderLayout());
        setResizable(false);
        initUI();
        pack();
        setLocationRelativeTo(owner);

        // Escape key to close
        getRootPane().registerKeyboardAction(
                e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );
    }

    private void initUI() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(Theme.CARD_BG);
        content.setBorder(BorderFactory.createEmptyBorder(Theme.SPACE_24, Theme.SPACE_32, Theme.SPACE_24, Theme.SPACE_32));

        JLabel title = new JLabel("New Customer");
        title.setFont(Theme.FONT_SECTION);
        title.setForeground(Theme.TEXT_PRIMARY);
        title.setAlignmentX(0.0f);
        content.add(title);
        content.add(Box.createVerticalStrut(Theme.SPACE_16));

        // Name
        content.add(createFieldLabel("Full Name *"));
        content.add(Box.createVerticalStrut(4));
        nameField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "e.g. Priya Patel");
        nameField.setPreferredSize(new Dimension(320, 38));
        nameField.setMaximumSize(new Dimension(320, 38));
        nameField.setAlignmentX(0.0f);
        content.add(nameField);
        styleErrorLabel(nameErrorLabel);
        content.add(nameErrorLabel);
        content.add(Box.createVerticalStrut(Theme.SPACE_8));

        // Phone
        content.add(createFieldLabel("Phone Number * (10 Digits)"));
        content.add(Box.createVerticalStrut(4));
        phoneField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "e.g. 9812345678");
        phoneField.setPreferredSize(new Dimension(320, 38));
        phoneField.setMaximumSize(new Dimension(320, 38));
        phoneField.setAlignmentX(0.0f);
        content.add(phoneField);
        styleErrorLabel(phoneErrorLabel);
        content.add(phoneErrorLabel);
        content.add(Box.createVerticalStrut(Theme.SPACE_8));

        // Email
        content.add(createFieldLabel("Email Address (Optional)"));
        content.add(Box.createVerticalStrut(4));
        emailField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "e.g. priya@example.com");
        emailField.setPreferredSize(new Dimension(320, 38));
        emailField.setMaximumSize(new Dimension(320, 38));
        emailField.setAlignmentX(0.0f);
        content.add(emailField);
        styleErrorLabel(emailErrorLabel);
        content.add(emailErrorLabel);
        content.add(Box.createVerticalStrut(Theme.SPACE_16));

        // Buttons
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.SPACE_8, 0));
        buttonRow.setOpaque(false);
        buttonRow.setAlignmentX(0.0f);

        cancelBtn = new JButton("Cancel");
        cancelBtn.setFont(Theme.FONT_BODY);
        cancelBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        cancelBtn.addActionListener(e -> dispose());
        buttonRow.add(cancelBtn);

        saveBtn = new JButton("Save & Select") {
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
        saveBtn.setFont(Theme.FONT_BODY_BOLD);
        saveBtn.setForeground(Color.WHITE);
        saveBtn.setContentAreaFilled(false);
        saveBtn.setBorderPainted(false);
        saveBtn.setFocusPainted(false);
        saveBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        saveBtn.setPreferredSize(new Dimension(130, 38));
        saveBtn.addActionListener(e -> onSave());
        buttonRow.add(saveBtn);

        content.add(buttonRow);
        add(content, BorderLayout.CENTER);
    }

    private void onSave() {
        clearErrors();

        Customer customer = new Customer(
                nameField.getText().trim(),
                phoneField.getText().trim(),
                emailField.getText().trim()
        );

        saveBtn.setEnabled(false);
        saveBtn.setText("Saving...");

        Ui.async(
                () -> customerService.createCustomer(customer),
                saved -> {
                    saveBtn.setEnabled(true);
                    saveBtn.setText("Save & Select");
                    dispose();
                    if (onCustomerSaved != null) {
                        onCustomerSaved.accept(saved);
                    }
                },
                error -> {
                    saveBtn.setEnabled(true);
                    saveBtn.setText("Save & Select");
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
                            nameErrorLabel.setText(ve.getMessage());
                        }
                    } else {
                        nameErrorLabel.setText(error.getMessage());
                    }
                }
        );
    }

    private void clearErrors() {
        nameErrorLabel.setText(" ");
        phoneErrorLabel.setText(" ");
        emailErrorLabel.setText(" ");

        nameField.putClientProperty(FlatClientProperties.OUTLINE, null);
        phoneField.putClientProperty(FlatClientProperties.OUTLINE, null);
        emailField.putClientProperty(FlatClientProperties.OUTLINE, null);
    }

    private JLabel createFieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.FONT_SMALL_BOLD);
        label.setForeground(Theme.TEXT_MUTED);
        label.setAlignmentX(0.0f);
        return label;
    }

    private void styleErrorLabel(JLabel label) {
        label.setFont(Theme.FONT_SMALL);
        label.setForeground(Theme.DANGER);
        label.setAlignmentX(0.0f);
    }
}
