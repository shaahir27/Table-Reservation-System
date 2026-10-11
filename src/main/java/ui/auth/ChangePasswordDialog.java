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
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.util.Arrays;

/**
 * Modal dialog for changing passwords with live strength indicator and leading icons.
 * Supports forced mode for temporary or first-time passwords where cancel is disallowed.
 */
public class ChangePasswordDialog extends JDialog {
    private final AuthService authService;
    private final AppUser user;
    private final boolean forced;

    private JPasswordField currentPasswordField;
    private JPasswordField newPasswordField;
    private JPasswordField confirmPasswordField;
    private StrengthBar strengthSegments;
    private JLabel strengthLabel;
    private JPanel errorBannerCard;
    private JLabel errorBannerLabel;
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
        JPanel content = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Top gold accent line
                LinearGradientPaint goldBar = new LinearGradientPaint(
                        0, 0, getWidth(), 0,
                        new float[]{0.0f, 0.5f, 1.0f},
                        new Color[]{
                                new Color(212, 175, 55),
                                new Color(200, 150, 62),
                                new Color(160, 115, 40)
                        }
                );
                g2.setPaint(goldBar);
                g2.fillRect(0, 0, getWidth(), 4);
                g2.dispose();
            }
        };
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(Theme.CARD_BG);
        content.setBorder(BorderFactory.createEmptyBorder(28, 32, 28, 32));

        JLabel title = new JLabel(forced ? "Password Change Required" : "Update Password");
        title.setFont(Theme.FONT_TITLE);
        title.setForeground(Theme.TEXT_PRIMARY);
        title.setAlignmentX(0.0f);

        JLabel sub = new JLabel("<html><div style='width:360px; color:#6B7078; font-size:12px; line-height:1.4;'>"
                + (forced ?
                "You are signed in with a temporary password. You must configure a new NIST-compliant password to continue." :
                "Ensure your new password contains at least 10 characters and avoids common dictionary words.")
                + "</div></html>");
        sub.setAlignmentX(0.0f);

        JLabel curLabel = new JLabel("CURRENT PASSWORD");
        curLabel.setFont(new Font("SansSerif", Font.BOLD, 10));
        curLabel.setForeground(Theme.TEXT_SECONDARY);
        curLabel.setAlignmentX(0.0f);

        currentPasswordField = new JPasswordField();
        currentPasswordField.setFont(Theme.FONT_BODY);
        currentPasswordField.setPreferredSize(new Dimension(360, 42));
        currentPasswordField.setMaximumSize(new Dimension(360, 42));
        currentPasswordField.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON,
                AuthIcons.lockIcon(18, new Color(148, 163, 184)));
        currentPasswordField.putClientProperty(FlatClientProperties.STYLE, "arc: 12; margin: 2,6,2,6; showRevealButton: true;");
        currentPasswordField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Enter current password");
        currentPasswordField.setAlignmentX(0.0f);

        JLabel newLabel = new JLabel("NEW PASSWORD (MIN 10 CHARS)");
        newLabel.setFont(new Font("SansSerif", Font.BOLD, 10));
        newLabel.setForeground(Theme.TEXT_SECONDARY);
        newLabel.setAlignmentX(0.0f);

        newPasswordField = new JPasswordField();
        newPasswordField.setFont(Theme.FONT_BODY);
        newPasswordField.setPreferredSize(new Dimension(360, 42));
        newPasswordField.setMaximumSize(new Dimension(360, 42));
        newPasswordField.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON,
                AuthIcons.lockIcon(18, new Color(148, 163, 184)));
        newPasswordField.putClientProperty(FlatClientProperties.STYLE, "arc: 12; margin: 2,6,2,6; showRevealButton: true;");
        newPasswordField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Enter new strong password");
        newPasswordField.setAlignmentX(0.0f);

        // 4-Segment Strength Meter
        strengthSegments = new StrengthBar();
        strengthSegments.setPreferredSize(new Dimension(360, 6));
        strengthSegments.setMaximumSize(new Dimension(360, 6));
        strengthSegments.setOpaque(false);
        strengthSegments.setAlignmentX(0.0f);

        strengthLabel = new JLabel("Password Strength: None");
        strengthLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        strengthLabel.setForeground(Theme.TEXT_MUTED);
        strengthLabel.setAlignmentX(0.0f);

        newPasswordField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { updateStrength(); }
            @Override public void removeUpdate(DocumentEvent e) { updateStrength(); }
            @Override public void changedUpdate(DocumentEvent e) { updateStrength(); }
        });

        JLabel confLabel = new JLabel("CONFIRM NEW PASSWORD");
        confLabel.setFont(new Font("SansSerif", Font.BOLD, 10));
        confLabel.setForeground(Theme.TEXT_SECONDARY);
        confLabel.setAlignmentX(0.0f);

        confirmPasswordField = new JPasswordField();
        confirmPasswordField.setFont(Theme.FONT_BODY);
        confirmPasswordField.setPreferredSize(new Dimension(360, 42));
        confirmPasswordField.setMaximumSize(new Dimension(360, 42));
        confirmPasswordField.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON,
                AuthIcons.shieldCheckIcon(18, new Color(148, 163, 184)));
        confirmPasswordField.putClientProperty(FlatClientProperties.STYLE, "arc: 12; margin: 2,6,2,6; showRevealButton: true;");
        confirmPasswordField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Re-enter new password");
        confirmPasswordField.setAlignmentX(0.0f);

        errorBannerCard = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(254, 242, 242));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(new Color(254, 202, 202));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        errorBannerCard.setOpaque(false);
        errorBannerCard.setLayout(new BorderLayout(8, 0));
        errorBannerCard.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        errorBannerCard.setPreferredSize(new Dimension(360, 36));
        errorBannerCard.setMaximumSize(new Dimension(360, 36));
        errorBannerCard.setVisible(false);
        errorBannerCard.setAlignmentX(0.0f);

        JLabel errorIcon = new JLabel("⚠️");
        errorIcon.setFont(new Font("SansSerif", Font.PLAIN, 14));
        errorIcon.setForeground(Theme.DANGER);

        errorBannerLabel = new JLabel("");
        errorBannerLabel.setFont(Theme.FONT_SMALL_BOLD);
        errorBannerLabel.setForeground(Theme.DANGER);

        errorBannerCard.add(errorIcon, BorderLayout.WEST);
        errorBannerCard.add(errorBannerLabel, BorderLayout.CENTER);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.setAlignmentX(0.0f);
        buttonPanel.setMaximumSize(new Dimension(360, 44));

        if (!forced) {
            JButton cancelBtn = new JButton("Cancel");
            cancelBtn.setFont(Theme.FONT_BODY);
            cancelBtn.addActionListener(e -> dispose());
            buttonPanel.add(cancelBtn);
        }

        saveButton = new JButton("Update Password  ➔") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();

                if (!isEnabled()) {
                    g2.setColor(new Color(148, 163, 184));
                    g2.fillRoundRect(0, 0, w, h, 12, 12);
                } else {
                    Color topColor = getModel().isRollover() ? new Color(225, 185, 75) : Theme.ACCENT_GOLD;
                    Color bottomColor = getModel().isRollover() ? Theme.ACCENT_GOLD : Theme.ACCENT_GOLD_HOVER;
                    if (getModel().isPressed()) {
                        topColor = new Color(165, 120, 40);
                        bottomColor = new Color(135, 95, 30);
                    }
                    LinearGradientPaint btnGrad = new LinearGradientPaint(
                            0, 0, 0, h,
                            new float[]{0.0f, 1.0f},
                            new Color[]{topColor, bottomColor}
                    );
                    g2.setPaint(btnGrad);
                    g2.fillRoundRect(0, 0, w, h, 12, 12);
                    g2.setColor(new Color(255, 255, 255, 45));
                    g2.fillRoundRect(0, 0, w, h / 2, 12, 12);
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        saveButton.setFont(new Font("SansSerif", Font.BOLD, 13));
        saveButton.setForeground(Color.WHITE);
        saveButton.setContentAreaFilled(false);
        saveButton.setBorderPainted(false);
        saveButton.setFocusPainted(false);
        saveButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        saveButton.setPreferredSize(new Dimension(160, 40));
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
        content.add(strengthSegments);
        content.add(Box.createVerticalStrut(Theme.SPACE_4));
        content.add(strengthLabel);
        content.add(Box.createVerticalStrut(Theme.SPACE_12));
        content.add(confLabel);
        content.add(Box.createVerticalStrut(Theme.SPACE_4));
        content.add(confirmPasswordField);
        content.add(Box.createVerticalStrut(Theme.SPACE_8));
        content.add(errorBannerCard);
        content.add(Box.createVerticalStrut(Theme.SPACE_16));
        content.add(buttonPanel);

        getContentPane().setBackground(Theme.CARD_BG);
        getContentPane().add(content);
    }

    private void updateStrength() {
        char[] pwd = newPasswordField.getPassword();
        int score = PasswordPolicy.calculateStrengthScore(pwd);
        strengthSegments.setScore(score);
        String label = switch (score) {
            case 1 -> "Very Weak";
            case 2 -> "Fair";
            case 3 -> "Good";
            case 4 -> "Strong";
            default -> pwd.length > 0 ? "Too short" : "None";
        };
        strengthLabel.setText("Password Strength: " + label);
    }

    private void performChange() {
        char[] cur = currentPasswordField.getPassword();
        char[] next = newPasswordField.getPassword();
        char[] conf = confirmPasswordField.getPassword();

        if (cur.length == 0 || next.length == 0 || conf.length == 0) {
            showError("Please fill out all fields.");
            return;
        }

        if (!Arrays.equals(next, conf)) {
            showError("New passwords do not match.");
            return;
        }

        PasswordPolicy.PolicyResult policy = PasswordPolicy.validate(next, user.getUsername());
        if (!policy.valid()) {
            showError(policy.getFirstError());
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
                    saveButton.setText("Update Password  ➔");
                    showError(error.getMessage() != null ? error.getMessage() : "Update failed.");
                }
        );
    }

    private void showError(String msg) {
        errorBannerLabel.setText("<html>" + msg + "</html>");
        errorBannerCard.setVisible(true);
    }

    private static class StrengthBar extends JPanel {
        private int score = 0;

        public void setScore(int s) {
            this.score = s;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int gap = 6;
            int segWidth = (w - (gap * 3)) / 4;

            Color activeColor = switch (score) {
                case 1 -> Theme.DANGER;
                case 2 -> Theme.WARNING;
                case 3 -> new Color(134, 239, 172);
                case 4 -> Theme.SUCCESS;
                default -> new Color(203, 213, 225);
            };

            for (int i = 0; i < 4; i++) {
                int x = i * (segWidth + gap);
                if (i < score) {
                    g2.setColor(activeColor);
                } else {
                    g2.setColor(new Color(226, 232, 240));
                }
                g2.fillRoundRect(x, 0, segWidth, h, 4, 4);
            }
            g2.dispose();
        }
    }
}
