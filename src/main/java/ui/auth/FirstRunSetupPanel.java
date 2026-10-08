package ui.auth;

import model.AppUser;
import security.PasswordPolicy;
import service.AuthService;
import ui.Theme;
import util.AppLog;
import util.Ui;

import com.formdev.flatlaf.FlatClientProperties;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.RenderingHints;
import java.util.Arrays;
import java.util.function.Consumer;

/**
 * Onboarding panel displayed on first launch when no staff accounts exist.
 * Enforces strong password setup for the primary Manager without hardcoding defaults.
 */
public class FirstRunSetupPanel extends JPanel {
    private final AuthService authService;
    private final Consumer<AppUser> onSetupComplete;

    private JTextField fullNameField;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JPasswordField confirmPasswordField;
    private JPanel strengthBar;
    private JLabel strengthLabel;
    private JLabel errorBanner;
    private JButton createButton;

    public FirstRunSetupPanel(AuthService authService, Consumer<AppUser> onSetupComplete) {
        this.authService = authService;
        this.onSetupComplete = onSetupComplete;

        setLayout(new GridBagLayout());
        setBackground(Theme.APP_BG);

        initUi();
    }

    private void initUi() {
        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.CARD_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                g2.setColor(Theme.CARD_BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 20, 20);
                g2.dispose();
            }
        };
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(36, 40, 36, 40));
        card.setPreferredSize(new Dimension(480, 620));
        card.setMaximumSize(new Dimension(480, 620));

        JLabel title = new JLabel("Initial System Setup");
        title.setFont(Theme.FONT_TITLE);
        title.setForeground(Theme.TEXT_PRIMARY);
        title.setAlignmentX(0.0f);

        JLabel subtitle = new JLabel("<html>Create the primary <b>Manager</b> account to initialize RTRS. No default passwords are used.</html>");
        subtitle.setFont(Theme.FONT_BODY);
        subtitle.setForeground(Theme.TEXT_MUTED);
        subtitle.setAlignmentX(0.0f);

        JLabel nameLabel = new JLabel("Full Name");
        nameLabel.setFont(Theme.FONT_BODY_BOLD);
        nameLabel.setForeground(Theme.TEXT_SECONDARY);
        nameLabel.setAlignmentX(0.0f);

        fullNameField = new JTextField();
        fullNameField.setFont(Theme.FONT_BODY);
        fullNameField.setPreferredSize(new Dimension(400, 40));
        fullNameField.setMaximumSize(new Dimension(400, 40));
        fullNameField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "e.g. Administrator");
        fullNameField.setAlignmentX(0.0f);

        JLabel userLabel = new JLabel("Username");
        userLabel.setFont(Theme.FONT_BODY_BOLD);
        userLabel.setForeground(Theme.TEXT_SECONDARY);
        userLabel.setAlignmentX(0.0f);

        usernameField = new JTextField();
        usernameField.setFont(Theme.FONT_BODY);
        usernameField.setPreferredSize(new Dimension(400, 40));
        usernameField.setMaximumSize(new Dimension(400, 40));
        usernameField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "e.g. admin or manager");
        usernameField.setAlignmentX(0.0f);

        JLabel passLabel = new JLabel("Password (min 10 characters)");
        passLabel.setFont(Theme.FONT_BODY_BOLD);
        passLabel.setForeground(Theme.TEXT_SECONDARY);
        passLabel.setAlignmentX(0.0f);

        passwordField = new JPasswordField();
        passwordField.setFont(Theme.FONT_BODY);
        passwordField.setPreferredSize(new Dimension(400, 40));
        passwordField.setMaximumSize(new Dimension(400, 40));
        passwordField.putClientProperty(FlatClientProperties.STYLE, "showRevealButton: true");
        passwordField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Enter strong password");
        passwordField.setAlignmentX(0.0f);

        // Strength Bar
        strengthBar = new JPanel() {
            private int score = 0;
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(255, 255, 255, 15));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 4, 4);

                Color barColor = switch (score) {
                    case 1 -> Theme.DANGER;
                    case 2 -> Theme.WARNING;
                    case 3 -> new Color(134, 239, 172);
                    case 4 -> Theme.SUCCESS;
                    default -> Color.DARK_GRAY;
                };

                int fillWidth = (int) (getWidth() * (score / 4.0));
                g2.setColor(barColor);
                g2.fillRoundRect(0, 0, fillWidth, getHeight(), 4, 4);
                g2.dispose();
            }

            public void setScore(int s) {
                this.score = s;
                repaint();
            }
        };
        strengthBar.setPreferredSize(new Dimension(400, 6));
        strengthBar.setMaximumSize(new Dimension(400, 6));
        strengthBar.setAlignmentX(0.0f);

        strengthLabel = new JLabel("Password Strength: None");
        strengthLabel.setFont(Theme.FONT_SMALL);
        strengthLabel.setForeground(Theme.TEXT_MUTED);
        strengthLabel.setAlignmentX(0.0f);

        passwordField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { updateStrength(); }
            @Override
            public void removeUpdate(DocumentEvent e) { updateStrength(); }
            @Override
            public void changedUpdate(DocumentEvent e) { updateStrength(); }
        });

        JLabel confirmLabel = new JLabel("Confirm Password");
        confirmLabel.setFont(Theme.FONT_BODY_BOLD);
        confirmLabel.setForeground(Theme.TEXT_SECONDARY);
        confirmLabel.setAlignmentX(0.0f);

        confirmPasswordField = new JPasswordField();
        confirmPasswordField.setFont(Theme.FONT_BODY);
        confirmPasswordField.setPreferredSize(new Dimension(400, 40));
        confirmPasswordField.setMaximumSize(new Dimension(400, 40));
        confirmPasswordField.putClientProperty(FlatClientProperties.STYLE, "showRevealButton: true");
        confirmPasswordField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Re-enter password");
        confirmPasswordField.setAlignmentX(0.0f);

        errorBanner = new JLabel("", SwingConstants.LEFT);
        errorBanner.setFont(Theme.FONT_SMALL);
        errorBanner.setForeground(Theme.DANGER);
        errorBanner.setVisible(false);
        errorBanner.setAlignmentX(0.0f);

        createButton = new JButton("Create Manager & Initialize") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (!isEnabled()) {
                    g2.setColor(new Color(100, 116, 139));
                } else if (getModel().isRollover()) {
                    g2.setColor(Theme.ACCENT_GOLD_HOVER);
                } else {
                    g2.setColor(Theme.ACCENT_GOLD);
                }
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        createButton.setFont(Theme.FONT_BODY_BOLD);
        createButton.setForeground(Color.WHITE);
        createButton.setContentAreaFilled(false);
        createButton.setBorderPainted(false);
        createButton.setFocusPainted(false);
        createButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        createButton.setPreferredSize(new Dimension(400, 44));
        createButton.setMaximumSize(new Dimension(400, 44));
        createButton.setAlignmentX(0.0f);
        createButton.addActionListener(e -> performSetup());

        card.add(title);
        card.add(Box.createVerticalStrut(Theme.SPACE_4));
        card.add(subtitle);
        card.add(Box.createVerticalStrut(Theme.SPACE_20));
        card.add(nameLabel);
        card.add(Box.createVerticalStrut(Theme.SPACE_4));
        card.add(fullNameField);
        card.add(Box.createVerticalStrut(Theme.SPACE_12));
        card.add(userLabel);
        card.add(Box.createVerticalStrut(Theme.SPACE_4));
        card.add(usernameField);
        card.add(Box.createVerticalStrut(Theme.SPACE_12));
        card.add(passLabel);
        card.add(Box.createVerticalStrut(Theme.SPACE_4));
        card.add(passwordField);
        card.add(Box.createVerticalStrut(Theme.SPACE_4));
        card.add(strengthBar);
        card.add(Box.createVerticalStrut(Theme.SPACE_4));
        card.add(strengthLabel);
        card.add(Box.createVerticalStrut(Theme.SPACE_12));
        card.add(confirmLabel);
        card.add(Box.createVerticalStrut(Theme.SPACE_4));
        card.add(confirmPasswordField);
        card.add(Box.createVerticalStrut(Theme.SPACE_12));
        card.add(errorBanner);
        card.add(Box.createVerticalStrut(Theme.SPACE_16));
        card.add(createButton);

        add(card);
    }

    private void updateStrength() {
        char[] pwd = passwordField.getPassword();
        int score = PasswordPolicy.calculateStrengthScore(pwd);
        ((JPanel) strengthBar).repaint();
        // Invoke custom setScore
        try {
            var method = strengthBar.getClass().getMethod("setScore", int.class);
            method.invoke(strengthBar, score);
        } catch (Exception ignored) {
        }

        String label = switch (score) {
            case 1 -> "Very Weak";
            case 2 -> "Fair";
            case 3 -> "Good";
            case 4 -> "Excellent";
            default -> pwd.length > 0 ? "Too short (<10 chars)" : "None";
        };
        strengthLabel.setText("Password Strength: " + label);
    }

    private void performSetup() {
        String name = fullNameField.getText().trim();
        String user = usernameField.getText().trim();
        char[] pass = passwordField.getPassword();
        char[] confirm = confirmPasswordField.getPassword();

        if (name.isBlank() || user.isBlank() || pass.length == 0) {
            showError("Please fill out all required fields.");
            return;
        }

        if (!Arrays.equals(pass, confirm)) {
            showError("Passwords do not match.");
            return;
        }

        PasswordPolicy.PolicyResult policy = PasswordPolicy.validate(pass, user);
        if (!policy.valid()) {
            showError(policy.getFirstError());
            return;
        }

        setFormEnabled(false);
        errorBanner.setVisible(false);

        Ui.async(
                () -> authService.setupFirstManager(user, name, pass),
                manager -> {
                    AppLog.info("Primary Manager account created successfully: " + manager.getUsername());
                    if (onSetupComplete != null) {
                        onSetupComplete.accept(manager);
                    }
                },
                error -> {
                    setFormEnabled(true);
                    showError(error.getMessage() != null ? error.getMessage() : "Error creating Manager account.");
                }
        );
    }

    private void showError(String msg) {
        errorBanner.setText("<html>" + msg + "</html>");
        errorBanner.setVisible(true);
    }

    private void setFormEnabled(boolean enabled) {
        fullNameField.setEnabled(enabled);
        usernameField.setEnabled(enabled);
        passwordField.setEnabled(enabled);
        confirmPasswordField.setEnabled(enabled);
        createButton.setEnabled(enabled);
        createButton.setText(enabled ? "Create Manager & Initialize" : "Creating Account...");
    }
}
