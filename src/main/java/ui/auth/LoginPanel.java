package ui.auth;

import exception.AccountLockedException;
import exception.AuthenticationException;
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
import javax.swing.SwingConstants;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

/**
 * Modern split-screen authentication panel (P1).
 * Features brand hero banner, FlatLaf reveal toggle, Caps-Lock warning,
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
    private JLabel errorBanner;
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
        // --- Left Brand Hero Banner (40%) ---
        JPanel brandPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Deep elegant dark gradient
                g2.setColor(Theme.SIDEBAR_BG);
                g2.fillRect(0, 0, getWidth(), getHeight());
                // Accent gold aesthetic bar on right border
                g2.setColor(Theme.CARD_BORDER);
                g2.drawLine(getWidth() - 1, 0, getWidth() - 1, getHeight());
                g2.dispose();
            }
        };
        brandPanel.setPreferredSize(new Dimension(460, 800));
        brandPanel.setLayout(new GridBagLayout());

        JPanel brandContent = new JPanel();
        brandContent.setLayout(new BoxLayout(brandContent, BoxLayout.Y_AXIS));
        brandContent.setOpaque(false);
        brandContent.setBorder(BorderFactory.createEmptyBorder(60, 50, 60, 50));

        JLabel logoLabel = new JLabel("Reserve");
        logoLabel.setFont(Theme.FONT_HERO);
        logoLabel.setForeground(Theme.ACCENT_GOLD);
        logoLabel.setAlignmentX(0.0f);

        JLabel tagLabel = new JLabel("Restaurant Table Reservation System");
        tagLabel.setFont(Theme.FONT_TITLE);
        tagLabel.setForeground(Color.WHITE);
        tagLabel.setAlignmentX(0.0f);

        JLabel subLabel = new JLabel("<html><div style='width:320px; line-height:1.6; color:#94A3B8;'>" +
                "High-precision table management, role-based security, concurrency-safe transactions, and live audit logging." +
                "</div></html>");
        subLabel.setFont(Theme.FONT_BODY);
        subLabel.setAlignmentX(0.0f);

        JLabel versionPill = new JLabel("  v2.0 • Enterprise Edition  ") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(212, 175, 55, 30));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.setColor(Theme.ACCENT_GOLD);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        versionPill.setFont(Theme.FONT_SMALL);
        versionPill.setForeground(Theme.ACCENT_GOLD);
        versionPill.setAlignmentX(0.0f);

        brandContent.add(logoLabel);
        brandContent.add(Box.createVerticalStrut(Theme.SPACE_8));
        brandContent.add(tagLabel);
        brandContent.add(Box.createVerticalStrut(Theme.SPACE_16));
        brandContent.add(subLabel);
        brandContent.add(Box.createVerticalStrut(Theme.SPACE_32));
        brandContent.add(versionPill);

        brandPanel.add(brandContent);
        add(brandPanel, BorderLayout.WEST);

        // --- Right Card Form Panel ---
        JPanel rightWrapper = new JPanel(new GridBagLayout());
        rightWrapper.setBackground(Theme.APP_BG);

        formCard = new JPanel() {
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
        formCard.setOpaque(false);
        formCard.setLayout(new BoxLayout(formCard, BoxLayout.Y_AXIS));
        formCard.setBorder(BorderFactory.createEmptyBorder(40, 40, 40, 40));
        formCard.setPreferredSize(new Dimension(420, 520));
        formCard.setMaximumSize(new Dimension(420, 520));

        JLabel welcomeTitle = new JLabel("Staff Sign In");
        welcomeTitle.setFont(Theme.FONT_TITLE);
        welcomeTitle.setForeground(Theme.TEXT_PRIMARY);
        welcomeTitle.setAlignmentX(0.0f);

        JLabel welcomeSub = new JLabel("Enter your credentials to access operations");
        welcomeSub.setFont(Theme.FONT_BODY);
        welcomeSub.setForeground(Theme.TEXT_MUTED);
        welcomeSub.setAlignmentX(0.0f);

        // Username Field
        JLabel userLabel = new JLabel("Username");
        userLabel.setFont(Theme.FONT_BODY_BOLD);
        userLabel.setForeground(Theme.TEXT_SECONDARY);
        userLabel.setAlignmentX(0.0f);

        usernameField = new JTextField();
        usernameField.setFont(Theme.FONT_BODY);
        usernameField.setPreferredSize(new Dimension(340, 42));
        usernameField.setMaximumSize(new Dimension(340, 42));
        usernameField.setAlignmentX(0.0f);
        usernameField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "e.g. manager, host1");

        String savedUser = prefs.get(PREF_REMEMBER_USER, "");
        if (!savedUser.isBlank()) {
            usernameField.setText(savedUser);
        }

        // Password Field
        JLabel passLabel = new JLabel("Password");
        passLabel.setFont(Theme.FONT_BODY_BOLD);
        passLabel.setForeground(Theme.TEXT_SECONDARY);
        passLabel.setAlignmentX(0.0f);

        passwordField = new JPasswordField();
        passwordField.setFont(Theme.FONT_BODY);
        passwordField.setPreferredSize(new Dimension(340, 42));
        passwordField.setMaximumSize(new Dimension(340, 42));
        passwordField.setAlignmentX(0.0f);
        passwordField.putClientProperty(FlatClientProperties.STYLE, "showRevealButton: true");
        passwordField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Enter password");

        // Caps Lock Indicator
        capsLockIndicator = new JLabel("⇪ Caps Lock is ON");
        capsLockIndicator.setFont(Theme.FONT_SMALL);
        capsLockIndicator.setForeground(Theme.ACCENT_GOLD);
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
        rememberCheck = new JCheckBox("Remember username");
        rememberCheck.setFont(Theme.FONT_SMALL);
        rememberCheck.setForeground(Theme.TEXT_MUTED);
        rememberCheck.setOpaque(false);
        rememberCheck.setSelected(!savedUser.isBlank());
        rememberCheck.setAlignmentX(0.0f);

        // Sign In Button
        signInButton = new JButton("Sign In") {
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
        signInButton.setFont(Theme.FONT_BODY_BOLD);
        signInButton.setForeground(Color.WHITE);
        signInButton.setContentAreaFilled(false);
        signInButton.setBorderPainted(false);
        signInButton.setFocusPainted(false);
        signInButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        signInButton.setPreferredSize(new Dimension(340, 44));
        signInButton.setMaximumSize(new Dimension(340, 44));
        signInButton.setAlignmentX(0.0f);
        signInButton.addActionListener(e -> performLogin());

        // Error Banner
        errorBanner = new JLabel("", SwingConstants.LEFT);
        errorBanner.setFont(Theme.FONT_SMALL);
        errorBanner.setForeground(Theme.DANGER);
        errorBanner.setVisible(false);
        errorBanner.setAlignmentX(0.0f);

        formCard.add(welcomeTitle);
        formCard.add(Box.createVerticalStrut(Theme.SPACE_4));
        formCard.add(welcomeSub);
        formCard.add(Box.createVerticalStrut(Theme.SPACE_24));
        formCard.add(userLabel);
        formCard.add(Box.createVerticalStrut(Theme.SPACE_8));
        formCard.add(usernameField);
        formCard.add(Box.createVerticalStrut(Theme.SPACE_16));
        formCard.add(passLabel);
        formCard.add(Box.createVerticalStrut(Theme.SPACE_8));
        formCard.add(passwordField);
        formCard.add(Box.createVerticalStrut(Theme.SPACE_4));
        formCard.add(capsLockIndicator);
        formCard.add(Box.createVerticalStrut(Theme.SPACE_8));
        formCard.add(rememberCheck);
        formCard.add(Box.createVerticalStrut(Theme.SPACE_16));
        formCard.add(errorBanner);
        formCard.add(Box.createVerticalStrut(Theme.SPACE_16));
        formCard.add(signInButton);

        rightWrapper.add(formCard);
        add(rightWrapper, BorderLayout.CENTER);
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
        errorBanner.setVisible(false);
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
        signInButton.setText(enabled ? "Sign In" : "Authenticating...");
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
        errorBanner.setVisible(false);

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
        errorBanner.setVisible(true);
        setFormEnabled(false);

        if (lockoutCountdownTimer != null) {
            lockoutCountdownTimer.stop();
        }

        lockoutCountdownTimer = new Timer(1000, e -> {
            secondsRemaining--;
            if (secondsRemaining <= 0) {
                lockoutCountdownTimer.stop();
                errorBanner.setText("Lockout expired. You may now sign in.");
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
        errorBanner.setText(String.format("Account temporarily locked (%02d:%02d remaining)", min, sec));
    }

    private void showError(String msg) {
        errorBanner.setText("<html>" + msg + "</html>");
        errorBanner.setVisible(true);
    }

    private void shakeCard() {
        final int originalX = formCard.getX();
        final int[] shakeOffsets = {-8, 8, -6, 6, -4, 4, -2, 0};
        Timer shakeTimer = new Timer(25, null);
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
