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
import java.awt.GridBagLayout;
import java.awt.LinearGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.util.Arrays;
import java.util.function.Consumer;

/**
 * High-End First-Run Onboarding Wizard (P1).
 * Displays when no staff accounts exist in Oracle XE.
 * Features unified luxury split-screen hero layout, live NIST password criteria pills,
 * 4-segment animated strength meter, and quick-fill demo presets.
 */
public class FirstRunSetupPanel extends JPanel {
    private final AuthService authService;
    private final Consumer<AppUser> onSetupComplete;

    private JTextField fullNameField;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JPasswordField confirmPasswordField;
    private StrengthBar strengthSegments;
    private JLabel strengthLabel;
    private JPanel errorBannerCard;
    private JLabel errorBannerLabel;
    private JButton createButton;

    public FirstRunSetupPanel(AuthService authService, Consumer<AppUser> onSetupComplete) {
        this.authService = authService;
        this.onSetupComplete = onSetupComplete;

        setLayout(new BorderLayout());
        setBackground(Theme.APP_BG);

        initUi();
    }

    private void initUi() {
        // --- Left Brand Hero Banner ---
        AuthHeroPanel heroPanel = new AuthHeroPanel();
        add(heroPanel, BorderLayout.WEST);

        // --- Right Card Panel ---
        JPanel rightWrapper = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int cx = getWidth() / 2;
                int cy = getHeight() / 2;
                float radius = Math.max(getWidth(), getHeight()) * 0.7f;
                RadialGradientPaint canvasGlow = new RadialGradientPaint(
                        cx, cy, radius,
                        new float[]{0.0f, 0.6f, 1.0f},
                        new Color[]{
                                new Color(255, 255, 255),
                                new Color(246, 244, 240),
                                new Color(238, 234, 226)
                        }
                );
                g2.setPaint(canvasGlow);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };

        // --- Floating Luxury Card with Drop Shadow & Gold Accent Bar ---
        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();

                // Multi-stage soft drop shadow
                for (int i = 1; i <= 4; i++) {
                    g2.setColor(new Color(0, 0, 0, 4 * i));
                    g2.fillRoundRect(4 - i, 4 - i, w - 8 + i * 2, h - 8 + i * 2, 24, 24);
                }

                // White Card Body
                g2.setColor(Theme.CARD_BG);
                g2.fillRoundRect(0, 0, w, h, 22, 22);

                // Top Luxury Gold Accent Bar (4px)
                LinearGradientPaint goldBar = new LinearGradientPaint(
                        0, 0, w, 0,
                        new float[]{0.0f, 0.5f, 1.0f},
                        new Color[]{
                                new Color(212, 175, 55),
                                new Color(200, 150, 62),
                                new Color(160, 115, 40)
                        }
                );
                g2.setPaint(goldBar);
                g2.fillRoundRect(0, 0, w, 5, 22, 22);
                g2.fillRect(0, 2, w, 3);

                // Card Border
                g2.setColor(Theme.CARD_BORDER);
                g2.drawRoundRect(0, 0, w - 1, h - 1, 22, 22);

                g2.dispose();
            }
        };
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(32, 40, 28, 40));
        card.setPreferredSize(new Dimension(470, 640));
        card.setMaximumSize(new Dimension(470, 640));

        // Step Badge
        JLabel stepBadge = new JLabel("  ★ SYSTEM ONBOARDING • STEP 1 OF 1  ") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(200, 150, 62, 25));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        stepBadge.setFont(new Font("SansSerif", Font.BOLD, 10));
        stepBadge.setForeground(Theme.ACCENT_GOLD);
        stepBadge.setAlignmentX(0.0f);

        JLabel title = new JLabel("Initialize Primary Manager");
        title.setFont(Theme.FONT_TITLE);
        title.setForeground(Theme.TEXT_PRIMARY);
        title.setAlignmentX(0.0f);

        JLabel subtitle = new JLabel("<html><div style='width:380px; color:#6B7078; font-size:12px; line-height:1.4;'>"
                + "Create the root administrator account to bootstrap RTRS. Zero default passwords are used."
                + "</div></html>");
        subtitle.setAlignmentX(0.0f);

        // Quick Fill Demo Button
        JPanel quickDemoRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        quickDemoRow.setOpaque(false);
        quickDemoRow.setAlignmentX(0.0f);

        JLabel quickLabel = new JLabel("Quick Setup:");
        quickLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        quickLabel.setForeground(Theme.TEXT_MUTED);
        quickDemoRow.add(quickLabel);

        JButton fillDemoBtn = new JButton("⚡ Fill Recommended Demo Credentials") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (getModel().isRollover()) {
                    g2.setColor(new Color(200, 150, 62, 35));
                } else {
                    g2.setColor(new Color(240, 238, 232));
                }
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(getModel().isRollover() ? Theme.ACCENT_GOLD : Theme.CARD_BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        fillDemoBtn.setFont(new Font("SansSerif", Font.BOLD, 10));
        fillDemoBtn.setForeground(Theme.TEXT_SECONDARY);
        fillDemoBtn.setContentAreaFilled(false);
        fillDemoBtn.setBorderPainted(false);
        fillDemoBtn.setFocusPainted(false);
        fillDemoBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        fillDemoBtn.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        fillDemoBtn.addActionListener(e -> {
            fullNameField.setText("Administrator");
            usernameField.setText("manager");
            passwordField.setText("Manager123!");
            confirmPasswordField.setText("Manager123!");
            updateStrength();
        });
        quickDemoRow.add(fillDemoBtn);

        // Full Name Field
        JLabel nameLabel = new JLabel("FULL NAME");
        nameLabel.setFont(new Font("SansSerif", Font.BOLD, 10));
        nameLabel.setForeground(Theme.TEXT_SECONDARY);
        nameLabel.setAlignmentX(0.0f);

        fullNameField = new JTextField();
        fullNameField.setFont(Theme.FONT_BODY);
        fullNameField.setPreferredSize(new Dimension(380, 42));
        fullNameField.setMaximumSize(new Dimension(380, 42));
        fullNameField.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON,
                AuthIcons.badgeIcon(18, new Color(148, 163, 184)));
        fullNameField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "e.g. Executive Chef / Manager");
        fullNameField.putClientProperty(FlatClientProperties.STYLE, "arc: 12; margin: 2,6,2,6;");
        fullNameField.setAlignmentX(0.0f);

        // Username Field
        JLabel userLabel = new JLabel("ROOT USERNAME");
        userLabel.setFont(new Font("SansSerif", Font.BOLD, 10));
        userLabel.setForeground(Theme.TEXT_SECONDARY);
        userLabel.setAlignmentX(0.0f);

        usernameField = new JTextField();
        usernameField.setFont(Theme.FONT_BODY);
        usernameField.setPreferredSize(new Dimension(380, 42));
        usernameField.setMaximumSize(new Dimension(380, 42));
        usernameField.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON,
                AuthIcons.userIcon(18, new Color(148, 163, 184)));
        usernameField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "e.g. manager or admin");
        usernameField.putClientProperty(FlatClientProperties.STYLE, "arc: 12; margin: 2,6,2,6;");
        usernameField.setAlignmentX(0.0f);

        // Password Field
        JLabel passLabel = new JLabel("STRONG PASSWORD (MIN 10 CHARS)");
        passLabel.setFont(new Font("SansSerif", Font.BOLD, 10));
        passLabel.setForeground(Theme.TEXT_SECONDARY);
        passLabel.setAlignmentX(0.0f);

        passwordField = new JPasswordField();
        passwordField.setFont(Theme.FONT_BODY);
        passwordField.setPreferredSize(new Dimension(380, 42));
        passwordField.setMaximumSize(new Dimension(380, 42));
        passwordField.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON,
                AuthIcons.lockIcon(18, new Color(148, 163, 184)));
        passwordField.putClientProperty(FlatClientProperties.STYLE, "arc: 12; margin: 2,6,2,6; showRevealButton: true;");
        passwordField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Enter strong password");
        passwordField.setAlignmentX(0.0f);

        // 4-Segment Modern Strength Meter
        strengthSegments = new StrengthBar();
        strengthSegments.setPreferredSize(new Dimension(380, 6));
        strengthSegments.setMaximumSize(new Dimension(380, 6));
        strengthSegments.setOpaque(false);
        strengthSegments.setAlignmentX(0.0f);

        strengthLabel = new JLabel("Password Strength: None");
        strengthLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
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

        // Confirm Password Field
        JLabel confirmLabel = new JLabel("CONFIRM PASSWORD");
        confirmLabel.setFont(new Font("SansSerif", Font.BOLD, 10));
        confirmLabel.setForeground(Theme.TEXT_SECONDARY);
        confirmLabel.setAlignmentX(0.0f);

        confirmPasswordField = new JPasswordField();
        confirmPasswordField.setFont(Theme.FONT_BODY);
        confirmPasswordField.setPreferredSize(new Dimension(380, 42));
        confirmPasswordField.setMaximumSize(new Dimension(380, 42));
        confirmPasswordField.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON,
                AuthIcons.shieldCheckIcon(18, new Color(148, 163, 184)));
        confirmPasswordField.putClientProperty(FlatClientProperties.STYLE, "arc: 12; margin: 2,6,2,6; showRevealButton: true;");
        confirmPasswordField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Re-enter password");
        confirmPasswordField.setAlignmentX(0.0f);

        // Error Banner Card
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
        errorBannerCard.setPreferredSize(new Dimension(380, 36));
        errorBannerCard.setMaximumSize(new Dimension(380, 36));
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

        // Create Manager Button
        createButton = new JButton("Create Manager & Initialize  ➔") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();

                if (!isEnabled()) {
                    g2.setColor(new Color(148, 163, 184));
                    g2.fillRoundRect(0, 0, w, h, 14, 14);
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
                    g2.fillRoundRect(0, 0, w, h, 14, 14);

                    // Top light sheen
                    g2.setColor(new Color(255, 255, 255, 45));
                    g2.fillRoundRect(0, 0, w, h / 2, 14, 14);
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        createButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        createButton.setForeground(Color.WHITE);
        createButton.setContentAreaFilled(false);
        createButton.setBorderPainted(false);
        createButton.setFocusPainted(false);
        createButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        createButton.setPreferredSize(new Dimension(380, 46));
        createButton.setMaximumSize(new Dimension(380, 46));
        createButton.setAlignmentX(0.0f);
        createButton.addActionListener(e -> performSetup());

        // Assembly
        card.add(stepBadge);
        card.add(Box.createVerticalStrut(Theme.SPACE_8));
        card.add(title);
        card.add(Box.createVerticalStrut(Theme.SPACE_4));
        card.add(subtitle);
        card.add(Box.createVerticalStrut(Theme.SPACE_12));
        card.add(quickDemoRow);
        card.add(Box.createVerticalStrut(Theme.SPACE_12));
        card.add(nameLabel);
        card.add(Box.createVerticalStrut(Theme.SPACE_4));
        card.add(fullNameField);
        card.add(Box.createVerticalStrut(Theme.SPACE_8));
        card.add(userLabel);
        card.add(Box.createVerticalStrut(Theme.SPACE_4));
        card.add(usernameField);
        card.add(Box.createVerticalStrut(Theme.SPACE_8));
        card.add(passLabel);
        card.add(Box.createVerticalStrut(Theme.SPACE_4));
        card.add(passwordField);
        card.add(Box.createVerticalStrut(Theme.SPACE_4));
        card.add(strengthSegments);
        card.add(Box.createVerticalStrut(Theme.SPACE_4));
        card.add(strengthLabel);
        card.add(Box.createVerticalStrut(Theme.SPACE_8));
        card.add(confirmLabel);
        card.add(Box.createVerticalStrut(Theme.SPACE_4));
        card.add(confirmPasswordField);
        card.add(Box.createVerticalStrut(Theme.SPACE_8));
        card.add(errorBannerCard);
        card.add(Box.createVerticalStrut(Theme.SPACE_12));
        card.add(createButton);

        rightWrapper.add(card);
        add(rightWrapper, BorderLayout.CENTER);
    }

    private void updateStrength() {
        char[] pwd = passwordField.getPassword();
        int score = PasswordPolicy.calculateStrengthScore(pwd);
        strengthSegments.setScore(score);

        String label = switch (score) {
            case 1 -> "Very Weak";
            case 2 -> "Fair";
            case 3 -> "Good";
            case 4 -> "Excellent (NIST Compliant)";
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
        errorBannerCard.setVisible(false);

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
        errorBannerLabel.setText("<html>" + msg + "</html>");
        errorBannerCard.setVisible(true);
    }

    private void setFormEnabled(boolean enabled) {
        fullNameField.setEnabled(enabled);
        usernameField.setEnabled(enabled);
        passwordField.setEnabled(enabled);
        confirmPasswordField.setEnabled(enabled);
        createButton.setEnabled(enabled);
        createButton.setText(enabled ? "Create Manager & Initialize  ➔" : "Creating Account...");
    }

    private static class StrengthBar extends JPanel {
        private int score = 0;

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

        public void setScore(int s) {
            this.score = s;
            repaint();
        }
    }
}
