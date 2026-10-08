package ui.auth;

import com.formdev.flatlaf.FlatClientProperties;
import model.AppUser;
import security.PasswordPolicy;
import service.AuthService;
import ui.Theme;
import util.AppLog;
import util.Ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Arrays;

/**
 * Modal dialog for changing passwords with live strength indicator.
 * Supports forced mode for temporary or first-time passwords where cancel is disallowed.
 */
public class ChangePasswordDialog extends JDialog {
    private final AuthService authService;
    private final AppUser user;
    private final boolean forced;

    private JPasswordField currentPasswordField;
    private JPasswordField newPasswordField;
    private JPasswordField confirmPasswordField;
    private JPanel strengthBar;
    private JLabel strengthLabel;
    private JLabel errorLabel;
    private JButton saveButton;

    public ChangePasswordDialog(JFrame parent, AuthService authService, AppUser user, boolean forced) {
        super(parent, forced ? "Mandatory Password Change" : "Change Password", true);
        this.authService = authService;
        this.user = user;
        this.forced = forced;

        if (forced) {
            setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        } else {
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        }

        initUi();
        pack();
        setLocationRelativeTo(parent);
    }

    private void initUi() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(Theme.CARD_BG);
        content.setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));

        JLabel title = new JLabel(forced ? "Password Change Required" : "Update Password");
        title.setFont(Theme.FONT_TITLE);
        title.setForeground(Theme.TEXT_PRIMARY);
        title.setAlignmentX(0.0f);

        JLabel sub = new JLabel(forced ?
                "You are using a temporary password. Please set a new password to continue." :
                "Ensure your new password is at least 10 characters long.");
        sub.setFont(Theme.FONT_SMALL);
        sub.setForeground(Theme.TEXT_MUTED);
        sub.setAlignmentX(0.0f);

        JLabel curLabel = new JLabel("Current Password");
        curLabel.setFont(Theme.FONT_BODY_BOLD);
        curLabel.setForeground(Theme.TEXT_SECONDARY);
        curLabel.setAlignmentX(0.0f);

        currentPasswordField = new JPasswordField();
        currentPasswordField.setFont(Theme.FONT_BODY);
        currentPasswordField.setPreferredSize(new Dimension(340, 38));
        currentPasswordField.setMaximumSize(new Dimension(340, 38));
        currentPasswordField.putClientProperty(FlatClientProperties.STYLE, "showRevealButton: true");
        currentPasswordField.setAlignmentX(0.0f);

        JLabel newLabel = new JLabel("New Password");
        newLabel.setFont(Theme.FONT_BODY_BOLD);
        newLabel.setForeground(Theme.TEXT_SECONDARY);
        newLabel.setAlignmentX(0.0f);

        newPasswordField = new JPasswordField();
        newPasswordField.setFont(Theme.FONT_BODY);
        newPasswordField.setPreferredSize(new Dimension(340, 38));
        newPasswordField.setMaximumSize(new Dimension(340, 38));
        newPasswordField.putClientProperty(FlatClientProperties.STYLE, "showRevealButton: true");
        newPasswordField.setAlignmentX(0.0f);

        // Strength Bar
        strengthBar = new JPanel() {
            private int score = 0;
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(255, 255, 255, 20));
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
        strengthBar.setPreferredSize(new Dimension(340, 6));
        strengthBar.setMaximumSize(new Dimension(340, 6));
        strengthBar.setAlignmentX(0.0f);

        strengthLabel = new JLabel("Strength: None");
        strengthLabel.setFont(Theme.FONT_SMALL);
        strengthLabel.setForeground(Theme.TEXT_MUTED);
        strengthLabel.setAlignmentX(0.0f);

        newPasswordField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { updateStrength(); }
            @Override public void removeUpdate(DocumentEvent e) { updateStrength(); }
            @Override public void changedUpdate(DocumentEvent e) { updateStrength(); }
        });

        JLabel confLabel = new JLabel("Confirm New Password");
        confLabel.setFont(Theme.FONT_BODY_BOLD);
        confLabel.setForeground(Theme.TEXT_SECONDARY);
        confLabel.setAlignmentX(0.0f);

        confirmPasswordField = new JPasswordField();
        confirmPasswordField.setFont(Theme.FONT_BODY);
        confirmPasswordField.setPreferredSize(new Dimension(340, 38));
        confirmPasswordField.setMaximumSize(new Dimension(340, 38));
        confirmPasswordField.putClientProperty(FlatClientProperties.STYLE, "showRevealButton: true");
        confirmPasswordField.setAlignmentX(0.0f);

        errorLabel = new JLabel("", SwingConstants.LEFT);
        errorLabel.setFont(Theme.FONT_SMALL);
        errorLabel.setForeground(Theme.DANGER);
        errorLabel.setVisible(false);
        errorLabel.setAlignmentX(0.0f);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.setAlignmentX(0.0f);
        buttonPanel.setMaximumSize(new Dimension(340, 42));

        if (!forced) {
            JButton cancelBtn = new JButton("Cancel");
            cancelBtn.setFont(Theme.FONT_BODY);
            cancelBtn.addActionListener(e -> dispose());
            buttonPanel.add(cancelBtn);
        }

        saveButton = new JButton("Update Password") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? Theme.ACCENT_GOLD_HOVER : Theme.ACCENT_GOLD);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        saveButton.setFont(Theme.FONT_BODY_BOLD);
        saveButton.setForeground(Color.WHITE);
        saveButton.setContentAreaFilled(false);
        saveButton.setBorderPainted(false);
        saveButton.setFocusPainted(false);
        saveButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        saveButton.setPreferredSize(new Dimension(140, 38));
        saveButton.addActionListener(e -> performChange());
        buttonPanel.add(saveButton);

        content.add(title);
        content.add(Box.createVerticalStrut(Theme.SPACE_4));
        content.add(sub);
        content.add(Box.createVerticalStrut(Theme.SPACE_16));
        content.add(curLabel);
        content.add(Box.createVerticalStrut(Theme.SPACE_4));
        content.add(currentPasswordField);
        content.add(Box.createVerticalStrut(Theme.SPACE_12));
        content.add(newLabel);
        content.add(Box.createVerticalStrut(Theme.SPACE_4));
        content.add(newPasswordField);
        content.add(Box.createVerticalStrut(Theme.SPACE_4));
        content.add(strengthBar);
        content.add(Box.createVerticalStrut(Theme.SPACE_4));
        content.add(strengthLabel);
        content.add(Box.createVerticalStrut(Theme.SPACE_12));
        content.add(confLabel);
        content.add(Box.createVerticalStrut(Theme.SPACE_4));
        content.add(confirmPasswordField);
        content.add(Box.createVerticalStrut(Theme.SPACE_8));
        content.add(errorLabel);
        content.add(Box.createVerticalStrut(Theme.SPACE_16));
        content.add(buttonPanel);

        getContentPane().setBackground(Theme.CARD_BG);
        getContentPane().add(content);
    }

    private void updateStrength() {
        char[] pwd = newPasswordField.getPassword();
        int score = PasswordPolicy.calculateStrengthScore(pwd);
        try {
            var method = strengthBar.getClass().getMethod("setScore", int.class);
            method.invoke(strengthBar, score);
        } catch (Exception ignored) {
        }
        String label = switch (score) {
            case 1 -> "Very Weak";
            case 2 -> "Fair";
            case 3 -> "Good";
            case 4 -> "Strong";
            default -> pwd.length > 0 ? "Too short" : "None";
        };
        strengthLabel.setText("Strength: " + label);
    }

    private void performChange() {
        char[] cur = currentPasswordField.getPassword();
        char[] next = newPasswordField.getPassword();
        char[] conf = confirmPasswordField.getPassword();

        if (cur.length == 0 || next.length == 0 || conf.length == 0) {
            errorLabel.setText("Please fill out all fields.");
            errorLabel.setVisible(true);
            return;
        }

        if (!Arrays.equals(next, conf)) {
            errorLabel.setText("New passwords do not match.");
            errorLabel.setVisible(true);
            return;
        }

        PasswordPolicy.PolicyResult policy = PasswordPolicy.validate(next, user.getUsername());
        if (!policy.valid()) {
            errorLabel.setText("<html>" + policy.getFirstError() + "</html>");
            errorLabel.setVisible(true);
            return;
        }

        saveButton.setEnabled(false);
        saveButton.setText("Updating...");

        Ui.async(
                () -> {
                    authService.changePassword(user.getUserId(), cur, next);
                    return true;
                },
                success -> {
                    AppLog.info("Password successfully changed for user " + user.getUsername());
                    dispose();
                },
                error -> {
                    saveButton.setEnabled(true);
                    saveButton.setText("Update Password");
                    errorLabel.setText("<html>" + (error.getMessage() != null ? error.getMessage() : "Update failed.") + "</html>");
                    errorLabel.setVisible(true);
                }
        );
    }
}
