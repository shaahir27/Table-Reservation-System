package ui.auth;

import exception.AccountLockedException;
import model.AppUser;
import service.AuthService;
import ui.Theme;
import util.AppLog;
import util.Ui;

import com.formdev.flatlaf.FlatClientProperties;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.Timer;
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
import java.awt.Toolkit;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.time.LocalTime;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

/**
 * State-of-the-Art Luxury Dining Authentication Panel (P1).
 * Features split hero layout, architectural CAD blueprint backdrop,
 * Java2D vector cloche crest, quick-select demo account pills,
 * FlatLaf leading vector icons, password reveal toggle, Caps-Lock warning,
 * DB-persisted lockout countdown timer, and asynchronous PBKDF2 derivation.
 */
public class LoginPanel extends JPanel {
    private static final String PREF_REMEMBER_USER = "rtrs.remembered_username";
    private final Preferences prefs = Preferences.userNodeForPackage(LoginPanel.class);

    private final AuthService authService;
    private final Consumer<AppUser> onLoginSuccess;
    private final Runnable onSetupRequested;

    private JTextField usernameField;
    private JPasswordField passwordField;
    private JCheckBox rememberCheck;
    private JButton signInButton;
    private JPanel errorBannerCard;
    private JLabel errorBannerLabel;
    private JLabel capsLockIndicator;
    private JPanel formCard;

    private Timer lockoutCountdownTimer;
    private long secondsRemaining = 0;

    public LoginPanel(AuthService authService, Consumer<AppUser> onLoginSuccess, Runnable onSetupRequested) {
        this.authService = authService;
        this.onLoginSuccess = onLoginSuccess;
        this.onSetupRequested = onSetupRequested;

        setLayout(new BorderLayout());
        setBackground(Theme.APP_BG);

        initUi();
    }

    private void initUi() {
        // --- Left Brand Hero Banner ---
        AuthHeroPanel heroPanel = new AuthHeroPanel();
        add(heroPanel, BorderLayout.WEST);

        // --- Right Card Form Panel ---
        JPanel rightWrapper = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Subtle warm luxury off-white ambient radial gradient
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
        formCard = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();

                // Multi-stage soft ambient drop shadow
                for (int i = 1; i <= 4; i++) {
                    g2.setColor(new Color(0, 0, 0, 4 * i));
                    g2.fillRoundRect(4 - i, 4 - i, w - 8 + i * 2, h - 8 + i * 2, 24, 24);
                }

                // Pure White Card Body
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
        formCard.setOpaque(false);
        formCard.setLayout(new BoxLayout(formCard, BoxLayout.Y_AXIS));
        formCard.setBorder(BorderFactory.createEmptyBorder(36, 40, 32, 40));
        formCard.setPreferredSize(new Dimension(450, 560));
        formCard.setMaximumSize(new Dimension(450, 560));

        // Time-aware greeting badge
        String greeting = getGreetingText();
        JLabel greetingBadge = new JLabel("  " + greeting + "  ") {
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
        greetingBadge.setFont(new Font("SansSerif", Font.BOLD, 10));
        greetingBadge.setForeground(Theme.ACCENT_GOLD);
        greetingBadge.setAlignmentX(0.0f);

        JLabel welcomeTitle = new JLabel("Staff Sign In");
        welcomeTitle.setFont(Theme.FONT_TITLE);
        welcomeTitle.setForeground(Theme.TEXT_PRIMARY);
        welcomeTitle.setAlignmentX(0.0f);

        JLabel welcomeSub = new JLabel("Enter your credentials to access this terminal.");
        welcomeSub.setFont(Theme.FONT_BODY);
        welcomeSub.setForeground(Theme.TEXT_MUTED);
        welcomeSub.setAlignmentX(0.0f);

        // Quick Demo Account Selectors
        JPanel quickDemoRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        quickDemoRow.setOpaque(false);
        quickDemoRow.setAlignmentX(0.0f);

        JLabel quickLabel = new JLabel("Demo Login:");
        quickLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        quickLabel.setForeground(Theme.TEXT_MUTED);
        quickDemoRow.add(quickLabel);

        JButton managerPill = createQuickRolePill("👑 Demo Manager", "manager", "Manager123!");
        JButton hostPill = createQuickRolePill("🛎️ Demo Host", "host", "HostPass123!");
        quickDemoRow.add(managerPill);
        quickDemoRow.add(hostPill);

        // Username Field with Vector User Icon
        JLabel userLabel = new JLabel("USERNAME OR STAFF ID");
        userLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
        userLabel.setForeground(Theme.TEXT_SECONDARY);
        userLabel.setAlignmentX(0.0f);

        usernameField = new JTextField();
        usernameField.setFont(Theme.FONT_BODY);
        usernameField.setPreferredSize(new Dimension(370, 44));
        usernameField.setMaximumSize(new Dimension(370, 44));
        usernameField.setAlignmentX(0.0f);
        usernameField.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON,
                AuthIcons.userIcon(18, new Color(148, 163, 184)));
        usernameField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "e.g. manager, host1");
        usernameField.putClientProperty(FlatClientProperties.STYLE, "arc: 12; margin: 2,6,2,6;");

        String savedUser = prefs.get(PREF_REMEMBER_USER, "");
        if (!savedUser.isBlank()) {
            usernameField.setText(savedUser);
        }

        // Password Field with Vector Padlock Icon
        JLabel passLabel = new JLabel("PASSWORD");
        passLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
        passLabel.setForeground(Theme.TEXT_SECONDARY);
        passLabel.setAlignmentX(0.0f);

        passwordField = new JPasswordField();
        passwordField.setFont(Theme.FONT_BODY);
        passwordField.setPreferredSize(new Dimension(370, 44));
        passwordField.setMaximumSize(new Dimension(370, 44));
        passwordField.setAlignmentX(0.0f);
        passwordField.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON,
                AuthIcons.lockIcon(18, new Color(148, 163, 184)));
        passwordField.putClientProperty(FlatClientProperties.STYLE, "arc: 12; margin: 2,6,2,6; showRevealButton: true;");
        passwordField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Enter your account password");

        // Caps Lock Warning Pill
        capsLockIndicator = new JLabel("  ⇪ Caps Lock is ON  ") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(245, 158, 11, 25));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(new Color(245, 158, 11, 80));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        capsLockIndicator.setFont(new Font("SansSerif", Font.BOLD, 11));
        capsLockIndicator.setForeground(new Color(180, 83, 9));
        capsLockIndicator.setVisible(false);
        capsLockIndicator.setAlignmentX(0.0f);

        KeyAdapter capsChecker = new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                checkCapsLock();
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    performLogin();
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                checkCapsLock();
            }
        };
        usernameField.addKeyListener(capsChecker);
        passwordField.addKeyListener(capsChecker);

        // Remember Username Checkbox
        rememberCheck = new JCheckBox("Remember username on this terminal");
        rememberCheck.setFont(Theme.FONT_SMALL);
        rememberCheck.setForeground(Theme.TEXT_MUTED);
        rememberCheck.setOpaque(false);
        rememberCheck.setSelected(!savedUser.isBlank());
        rememberCheck.setAlignmentX(0.0f);

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
        errorBannerCard.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        errorBannerCard.setPreferredSize(new Dimension(370, 38));
        errorBannerCard.setMaximumSize(new Dimension(370, 38));
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

        // High-Impact Sign In Button
        signInButton = new JButton("Sign In to Workspace  ➔") {
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
        signInButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        signInButton.setForeground(Color.WHITE);
        signInButton.setContentAreaFilled(false);
        signInButton.setBorderPainted(false);
        signInButton.setFocusPainted(false);
        signInButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        signInButton.setPreferredSize(new Dimension(370, 46));
        signInButton.setMaximumSize(new Dimension(370, 46));
        signInButton.setAlignmentX(0.0f);
        signInButton.addActionListener(e -> performLogin());

        // Footer Security Caption
        JPanel footerSecurity = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
        footerSecurity.setOpaque(false);
        footerSecurity.setAlignmentX(0.0f);

        JLabel lockBadge = new JLabel("🔒 PBKDF2-HMAC • NIST SP 800-63B • Isolated Session");
        lockBadge.setFont(new Font("SansSerif", Font.PLAIN, 10));
        lockBadge.setForeground(Theme.TEXT_MUTED);
        footerSecurity.add(lockBadge);

        if (onSetupRequested != null) {
            JButton setupBtn = new JButton("• Initial Setup");
            setupBtn.setFont(new Font("SansSerif", Font.PLAIN, 10));
            setupBtn.setForeground(Theme.TEXT_MUTED);
            setupBtn.setContentAreaFilled(false);
            setupBtn.setBorderPainted(false);
            setupBtn.setFocusPainted(false);
            setupBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setupBtn.addActionListener(e -> onSetupRequested.run());
            footerSecurity.add(setupBtn);
        }

        // Assembly
        formCard.add(greetingBadge);
        formCard.add(Box.createVerticalStrut(Theme.SPACE_8));
        formCard.add(welcomeTitle);
        formCard.add(Box.createVerticalStrut(Theme.SPACE_4));
        formCard.add(welcomeSub);
        formCard.add(Box.createVerticalStrut(Theme.SPACE_16));
        formCard.add(quickDemoRow);
        formCard.add(Box.createVerticalStrut(Theme.SPACE_16));
        formCard.add(userLabel);
        formCard.add(Box.createVerticalStrut(Theme.SPACE_4));
        formCard.add(usernameField);
        formCard.add(Box.createVerticalStrut(Theme.SPACE_12));
        formCard.add(passLabel);
        formCard.add(Box.createVerticalStrut(Theme.SPACE_4));
        formCard.add(passwordField);
        formCard.add(Box.createVerticalStrut(Theme.SPACE_4));
        formCard.add(capsLockIndicator);
        formCard.add(Box.createVerticalStrut(Theme.SPACE_8));
        formCard.add(rememberCheck);
        formCard.add(Box.createVerticalStrut(Theme.SPACE_12));
        formCard.add(errorBannerCard);
        formCard.add(Box.createVerticalStrut(Theme.SPACE_16));
        formCard.add(signInButton);
        formCard.add(Box.createVerticalGlue());
        formCard.add(footerSecurity);

        rightWrapper.add(formCard);
        add(rightWrapper, BorderLayout.CENTER);
    }

    private String getGreetingText() {
        int hour = LocalTime.now().getHour();
        if (hour >= 4 && hour < 12) {
            return "☀️ GOOD MORNING";
        } else if (hour >= 12 && hour < 17) {
            return "☀️ GOOD AFTERNOON";
        } else {
            return "🌙 GOOD EVENING";
        }
    }

    private JButton createQuickRolePill(String label, String usernameValue, String passwordValue) {
        JButton pill = new JButton(label) {
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
        pill.setFont(new Font("SansSerif", Font.BOLD, 10));
        pill.setForeground(Theme.TEXT_SECONDARY);
        pill.setContentAreaFilled(false);
        pill.setBorderPainted(false);
        pill.setFocusPainted(false);
        pill.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        pill.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        pill.setToolTipText("Click to auto-fill " + usernameValue + " credentials");
        pill.addActionListener(e -> {
            usernameField.setText(usernameValue);
            passwordField.setText(passwordValue);
            errorBannerCard.setVisible(false);
            signInButton.requestFocusInWindow();
        });
        return pill;
    }

    private void checkCapsLock() {
        try {
            boolean caps = Toolkit.getDefaultToolkit().getLockingKeyState(KeyEvent.VK_CAPS_LOCK);
            capsLockIndicator.setVisible(caps);
        } catch (Exception ignored) {
        }
    }

    public void resetForm() {
        if (lockoutCountdownTimer != null) {
            lockoutCountdownTimer.stop();
        }
        errorBannerCard.setVisible(false);
        passwordField.setText("");
        setFormEnabled(true);
        String savedUser = prefs.get(PREF_REMEMBER_USER, "");
        if (!savedUser.isBlank()) {
            usernameField.setText(savedUser);
            passwordField.requestFocusInWindow();
        } else {
            usernameField.requestFocusInWindow();
        }
    }

    private void setFormEnabled(boolean enabled) {
        usernameField.setEnabled(enabled);
        passwordField.setEnabled(enabled);
        rememberCheck.setEnabled(enabled);
        signInButton.setEnabled(enabled);
        signInButton.setText(enabled ? "Sign In to Workspace  ➔" : "Authenticating...");
    }

    private void performLogin() {
        String username = usernameField.getText().trim();
        char[] password = passwordField.getPassword();

        if (username.isBlank() || password.length == 0) {
            showError("Please enter both username and password.");
            shakeCard();
            return;
        }

        setFormEnabled(false);
        errorBannerCard.setVisible(false);

        Ui.async(
                () -> authService.login(username, password),
                user -> {
                    // Save or clear remembered username
                    if (rememberCheck.isSelected()) {
                        prefs.put(PREF_REMEMBER_USER, username);
                    } else {
                        prefs.remove(PREF_REMEMBER_USER);
                    }
                    setFormEnabled(true);
                    passwordField.setText("");
                    AppLog.info("Login successful for user: " + user.getUsername());
                    if (onLoginSuccess != null) {
                        onLoginSuccess.accept(user);
                    }
                },
                error -> {
                    setFormEnabled(true);
                    shakeCard();
                    if (error instanceof AccountLockedException lockedEx) {
                        handleLockout(lockedEx.getSecondsRemaining());
                    } else {
                        showError(error.getMessage() != null ? error.getMessage() : "Invalid credentials.");
                    }
                    passwordField.setText("");
                    passwordField.requestFocusInWindow();
                }
        );
    }

    private void handleLockout(long seconds) {
        this.secondsRemaining = seconds;
        updateLockoutText();
        errorBannerCard.setVisible(true);
        setFormEnabled(false);

        if (lockoutCountdownTimer != null) {
            lockoutCountdownTimer.stop();
        }

        lockoutCountdownTimer = new Timer(1000, e -> {
            secondsRemaining--;
            if (secondsRemaining <= 0) {
                lockoutCountdownTimer.stop();
                errorBannerLabel.setText("Lockout expired. You may now sign in.");
                setFormEnabled(true);
            } else {
                updateLockoutText();
            }
        });
        lockoutCountdownTimer.start();
    }

    private void updateLockoutText() {
        long min = secondsRemaining / 60;
        long sec = secondsRemaining % 60;
        errorBannerLabel.setText(String.format("Account temporarily locked (%02d:%02d remaining)", min, sec));
    }

    private void showError(String msg) {
        errorBannerLabel.setText("<html>" + msg + "</html>");
        errorBannerCard.setVisible(true);
    }

    private void shakeCard() {
        final int originalX = formCard.getX();
        final int[] shakeOffsets = {-10, 10, -8, 8, -5, 5, -2, 0};
        Timer shakeTimer = new Timer(22, null);
        shakeTimer.addActionListener(new java.awt.event.ActionListener() {
            int frame = 0;
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (frame < shakeOffsets.length) {
                    formCard.setLocation(originalX + shakeOffsets[frame], formCard.getY());
                    frame++;
                } else {
                    formCard.setLocation(originalX, formCard.getY());
                    shakeTimer.stop();
                }
            }
        });
        shakeTimer.start();
    }
}
