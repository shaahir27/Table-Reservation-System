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
        setSize(Math.max(getWidth(), 460), getHeight());
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
        content.add(Box.createVerticalStrut(6));
        nameField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "e.g. Priya Patel");
        Form.input(nameField);
        content.add(nameField);
        styleErrorLabel(nameErrorLabel);
        content.add(nameErrorLabel);
        content.add(Box.createVerticalStrut(Theme.SPACE_8));

        // Phone
        content.add(createFieldLabel("Phone Number * (10 Digits)"));
        content.add(Box.createVerticalStrut(6));
        phoneField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "e.g. 9812345678");
        Form.input(phoneField);
        content.add(phoneField);
        styleErrorLabel(phoneErrorLabel);
        content.add(phoneErrorLabel);
        content.add(Box.createVerticalStrut(Theme.SPACE_8));

        // Email
        content.add(createFieldLabel("Email Address (Optional)"));
        content.add(Box.createVerticalStrut(6));
        emailField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "e.g. priya@example.com");
        Form.input(emailField);
        content.add(emailField);
        styleErrorLabel(emailErrorLabel);
        content.add(emailErrorLabel);
        content.add(Box.createVerticalStrut(Theme.SPACE_16));

        // Buttons
        JPanel buttonRow = new ChipRow(Theme.SPACE_8, true);
        buttonRow.setOpaque(false);
        buttonRow.setAlignmentX(0.0f);

        cancelBtn = AppButton.secondary("Cancel");
        cancelBtn.addActionListener(e -> dispose());
        buttonRow.add(cancelBtn);

        saveBtn = AppButton.primary("Save & Select");
        saveBtn.addActionListener(e -> onSave());
        buttonRow.add(saveBtn);
        getRootPane().setDefaultButton(saveBtn);

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

        ((AppButton) saveBtn).setLoading(true);
        saveBtn.setText("Saving…");

        Ui.async(
                () -> customerService.createCustomer(customer),
                saved -> {
                    ((AppButton) saveBtn).setLoading(false);
                    saveBtn.setText("Save & Select");
                    dispose();
                    if (onCustomerSaved != null) {
                        onCustomerSaved.accept(saved);
                    }
                },
                error -> {
                    ((AppButton) saveBtn).setLoading(false);
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
